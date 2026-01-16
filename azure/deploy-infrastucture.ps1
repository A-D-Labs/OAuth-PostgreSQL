# PowerShell script to deploy Azure infrastructure using ARM templates
param(
    [Parameter(Mandatory = $true)]
    [string]$Environment,

    [Parameter(Mandatory = $false)]
    [string]$ResourceGroupName = "oauth-template-$Environment",

    [Parameter(Mandatory = $false)]
    [string]$Location = "eastus",

    [Parameter(Mandatory = $false)]
    [string]$TemplateFile = "arm-template.json",

    [Parameter(Mandatory = $false)]
    [hashtable]$AdditionalParameters = @{}
)

# Check if the specified environment is valid
if ($Environment -notin @("dev", "pat", "prod")) {
    Write-Error "Invalid environment. Please specify 'dev', 'pat', or 'prod'."
    exit 1
}

# Login to Azure (if not already logged in)
$context = Get-AzContext
if (!$context) {
    Write-Host "Please login to Azure..."
    Connect-AzAccount
}

# Create resource group if it doesn't exist
$resourceGroup = Get-AzResourceGroup -Name $ResourceGroupName -ErrorAction SilentlyContinue
if (!$resourceGroup) {
    Write-Host "Creating resource group: $ResourceGroupName in location: $Location"
    New-AzResourceGroup -Name $ResourceGroupName -Location $Location
}

# Prepare ARM template parameters
$templateParameters = @{
    environment = $Environment
}

# Add dynamic parameters based on environment
switch ($Environment) {
    "dev" {
        $templateParameters["skuName"] = "B1"
        $templateParameters["capacity"] = 1
    }
    "pat" {
        $templateParameters["skuName"] = "S1"
        $templateParameters["capacity"] = 1
    }
    "prod" {
        $templateParameters["skuName"] = "P1v2"
        $templateParameters["capacity"] = 2
    }
}

# Generate a random MySQL admin password if not provided
if (!$AdditionalParameters.ContainsKey("mysqlAdminPassword")) {
    $mysqlPassword = [System.Web.Security.Membership]::GeneratePassword(16, 4)
    $securePassword = ConvertTo-SecureString $mysqlPassword -AsPlainText -Force
    $templateParameters["mysqlAdminPassword"] = $securePassword

    Write-Host "Generated MySQL admin password. Make sure to save this password securely."
    Write-Host "MySQL Password: $mysqlPassword"
}

# Set MySQL admin username if not provided
if (!$AdditionalParameters.ContainsKey("mysqlAdminUsername")) {
    $templateParameters["mysqlAdminUsername"] = "oauthadmin"
}

# Merge additional parameters
foreach ($key in $AdditionalParameters.Keys) {
    $templateParameters[$key] = $AdditionalParameters[$key]
}

# Deploy the ARM template
Write-Host "Deploying infrastructure for $Environment environment..."
$deployment = New-AzResourceGroupDeployment `
    -ResourceGroupName $ResourceGroupName `
    -TemplateFile $TemplateFile `
    -TemplateParameterObject $templateParameters `
    -Mode Incremental `
    -Verbose

# Output deployment results
if ($deployment.ProvisioningState -eq "Succeeded") {
    Write-Host "Deployment completed successfully!" -ForegroundColor Green

    # Display important outputs
    Write-Host "Web App URL: $($deployment.Outputs.webAppUrl.Value)"
    Write-Host "MySQL Server FQDN: $($deployment.Outputs.mysqlServerFqdn.Value)"
    Write-Host "Container Registry URL: $($deployment.Outputs.containerRegistryUrl.Value)"
    Write-Host "Key Vault URI: $($deployment.Outputs.keyVaultUri.Value)"
    Write-Host "Redis Host: $($deployment.Outputs.redisHostName.Value)"

    # For dev environment, output additional information
    if ($Environment -eq "dev") {
        Write-Host "Application Insights Instrumentation Key: $($deployment.Outputs.appInsightsInstrumentationKey.Value)"
    }

    # Save outputs to a file for later reference
    $deployment.Outputs | ConvertTo-Json | Out-File -FilePath "deployment-outputs-$Environment.json"
    Write-Host "Deployment outputs saved to: deployment-outputs-$Environment.json"

    # Next steps
    Write-Host "`nNext steps:" -ForegroundColor Yellow
    Write-Host "1. Update your Azure DevOps variable groups with the connection details"
    Write-Host "2. Set up the JWT secrets and OAuth credentials in Key Vault"
    Write-Host "   az keyvault secret set --vault-name $($deployment.Outputs.keyVaultUri.Value.Split('/')[2]) --name JWT-SECRET --value 'your-jwt-secret'"
    Write-Host "3. Configure the OAuth application with the Web App URL for redirects"

    # Store MySQL password in Key Vault if requested
    if ($AdditionalParameters.ContainsKey("storePasswordInKeyVault") -and $AdditionalParameters["storePasswordInKeyVault"] -eq $true) {
        $keyVaultName = $deployment.Outputs.keyVaultUri.Value.Split('/')[2]
        Write-Host "Storing MySQL password in Key Vault: $keyVaultName"

        if ($templateParameters.ContainsKey("mysqlAdminPassword")) {
            $pw = if ($templateParameters["mysqlAdminPassword"] -is [SecureString]) {
                (New-Object PSCredential "dummy", $templateParameters["mysqlAdminPassword"]).GetNetworkCredential().Password
            } else {
                $templateParameters["mysqlAdminPassword"]
            }

            Set-AzKeyVaultSecret -VaultName $keyVaultName -Name "MySQLAdminPassword" -SecretValue (ConvertTo-SecureString $pw -AsPlainText -Force)
        }
    }
} else {
    Write-Host "Deployment failed with status: $($deployment.ProvisioningState)" -ForegroundColor Red
    Write-Host "Please check the Azure portal for detailed error messages."
    exit 1
}

# Setup Azure DevOps build service principal access
if ($AdditionalParameters.ContainsKey("grantBuildServiceAccess") -and $AdditionalParameters["grantBuildServiceAccess"] -eq $true) {
    if ($AdditionalParameters.ContainsKey("buildServicePrincipalId")) {
        $spId = $AdditionalParameters["buildServicePrincipalId"]
        $keyVaultName = $deployment.Outputs.keyVaultUri.Value.Split('/')[2]

        Write-Host "Granting Azure DevOps build service principal access to resources..."

        # Grant Key Vault access
        Set-AzKeyVaultAccessPolicy -VaultName $keyVaultName -ObjectId $spId -PermissionsToSecrets get,list

        # Grant ACR access (Pull)
        $acrName = $deployment.Outputs.containerRegistryUrl.Value.Split('.')[0]
        New-AzRoleAssignment -ObjectId $spId -RoleDefinitionName "AcrPull" -ResourceName $acrName -ResourceType "Microsoft.ContainerRegistry/registries" -ResourceGroupName $ResourceGroupName

        # For production, also grant push access
        if ($Environment -eq "prod") {
            New-AzRoleAssignment -ObjectId $spId -RoleDefinitionName "AcrPush" -ResourceName $acrName -ResourceType "Microsoft.ContainerRegistry/registries" -ResourceGroupName $ResourceGroupName
        }

        Write-Host "Service principal access granted successfully"
    } else {
        Write-Host "Service principal ID not provided. Skipping service principal access setup." -ForegroundColor Yellow
    }
}

# Configure CI/CD connection
if ($AdditionalParameters.ContainsKey("setupAzureDevOpsService") -and $AdditionalParameters["setupAzureDevOpsService"] -eq $true) {
    # This requires Azure CLI DevOps extension
    Write-Host "Checking Azure CLI DevOps extension..."
    $extensionCheck = az extension list --query "[?name=='azure-devops'].name" -o tsv

    if (-not $extensionCheck) {
        Write-Host "Installing Azure DevOps CLI extension..."
        az extension add --name azure-devops
    }

    # Check if Azure DevOps organization and project are provided
    if ($AdditionalParameters.ContainsKey("azureDevOpsOrg") -and $AdditionalParameters.ContainsKey("azureDevOpsProject")) {
        $org = $AdditionalParameters["azureDevOpsOrg"]
        $project = $AdditionalParameters["azureDevOpsProject"]

        Write-Host "Setting up Azure DevOps service connection for $Environment environment..."

        # Create service connection
        az devops configure --defaults organization="https://dev.azure.com/$org" project="$project"

        $serviceEndpointName = "OAuth-$Environment-Azure"
        $subscriptionId = (Get-AzContext).Subscription.Id
        $subscriptionName = (Get-AzContext).Subscription.Name

        az devops service-endpoint azurerm create --azure-rm-service-principal-id "automatic" `
            --azure-rm-subscription-id $subscriptionId `
            --azure-rm-subscription-name $subscriptionName `
            --azure-rm-tenant-id (Get-AzContext).Tenant.Id `
            --name $serviceEndpointName

        Write-Host "Azure DevOps service connection '$serviceEndpointName' created successfully" -ForegroundColor Green
    } else {
        Write-Host "Azure DevOps organization and project not provided. Skipping service connection setup." -ForegroundColor Yellow
    }
}

Write-Host "`nInfrastructure deployment for $Environment environment completed!" -ForegroundColor Green
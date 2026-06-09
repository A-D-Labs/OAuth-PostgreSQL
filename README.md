# OAuth 2.0 Authentication Template

A comprehensive Spring Boot template for implementing OAuth 2.0 authentication with multiple providers, including Google, Spotify, Apple, and SoundCloud. This template also includes email/password authentication with email verification.

## 🌟 Features

- **Multiple Authentication Methods**:
    - OAuth 2.0 integration with Google, Spotify, Apple, and SoundCloud
    - Traditional email/password authentication
    - Email verification for account activation
    - Password reset functionality

- **User Management**:
    - Role-based access control (User, Admin, Moderator, Premium)
    - Profile management with update history
    - Notification preferences
    - Theme preferences (Light/Dark/System)
    - Language/Internationalization support (English, Spanish, French, German)

- **Security Features**:
    - JWT authentication with refresh tokens
    - HTTP-only cookies for token storage
    - Rate limiting to prevent brute force attacks
    - Comprehensive audit logging
    - CSRF protection
    - XSS protection

- **API Documentation**:
    - Swagger/OpenAPI integration
    - Grouped API endpoints by functionality (Authentication, User Management, Administration)

- **Monitoring and Metrics**:
    - Spring Actuator integration
    - Micrometer metrics
    - Prometheus compatibility
    - Custom health indicators
    - Audit logging

- **DevOps Ready**:
    - Docker support with multi-stage builds
    - Environment-specific configurations (dev, test, pat, prod)
    - Azure DevOps pipeline configuration
    - Infrastructure as Code with ARM templates

## 🚀 Getting Started

> ⚠️ **Security Note**: This template follows the `.env` approach for configuration. These files will contain sensitive information and should NEVER be committed to your Git repository. The `.gitignore` file is configured to exclude all `.env.*` files. Always create these files locally and securely share them with your team members outside of your version control system.

### Prerequisites

- Java 21 or later
- Maven 3.8 or later
- PostgreSQL 14 or later
- Docker and Docker Compose (optional)

### Setup Instructions

1. **Clone the repository**:
   ```bash
   git clone https://github.com/Nootje88/oauth.git
   cd oauth-template
   ```

2. **Create environment file**:
   Copy the template environment file and customize it for each environment:
   ```bash
   cp .env.template .env.dev
   # Also create for other environments as needed
   # cp .env.template .env.test
   # cp .env.template .env.pat
   # cp .env.template .env.prod
   ```

   > ⚠️ **IMPORTANT**: All `.env.*` files contain sensitive information and should NEVER be committed to your repository. Make sure they are included in your `.gitignore` file!

3. **Update environment variables**:
   Open `.env.dev` and update the following required variables:
    - `DB_USERNAME`: Your PostgreSQL username
    - `DB_PASSWORD`: Your PostgreSQL password
    - `DB_URL`: Your PostgreSQL connection URL (e.g. `jdbc:postgresql://localhost:5432/oauth_dev`)
    - `JWT_SECRET`: A strong, random secret key for JWT signing
    - `GOOGLE_CLIENT_ID`: Your Google OAuth client ID
    - `GOOGLE_CLIENT_SECRET`: Your Google OAuth client secret
    - `EMAIL_USERNAME`: Your email service username
    - `EMAIL_PASSWORD`: Your email service password

4. **Build the application**:
   ```bash
   ./mvnw clean package
   ```

   Build a container image without a Docker daemon (via Jib):
   ```bash
   ./mvnw compile jib:dockerBuild   # build to the local Docker daemon
   ./mvnw compile jib:build         # build & push to a registry
   ```

5. **Run the application**:
   With Maven:
   ```bash
   ./mvnw spring-boot:run -Dspring.profiles.active=dev
   ```

   With Docker:
   ```bash
   docker-compose -f docker/compose/docker-compose.yml -f docker/compose/docker-compose.dev.yml up
   ```

   Or use the provided script:
   ```bash
   ./docker/scripts/switch-env.sh dev
   ```

6. **Access the application**:
    - API: http://localhost:8080
    - Swagger UI: http://localhost:8080/swagger-ui.html

### Running in IntelliJ IDEA

To run the application in IntelliJ IDEA:

1. Open Run/Debug Configurations (Run → Edit Configurations...)
2. Click the + button and select "Spring Boot"
3. Configure the following settings:
    * **Name**: OAuth Template (Dev)
    * **Main class**: `com.template.OAuth.OAuthApplication`
    * **VM options**: `-Dspring.profiles.active=dev`
    * **Working directory**: `$MODULE_WORKING_DIR$`
    * **Use classpath of module**: Select the module with your application
4. Click "Apply" then "OK"
5. Run the configuration from the main toolbar

## 🔧 Configuration

### Environment Files

This project uses separate environment files for different deployment environments:

- `.env.dev` - Development environment
- `.env.test` - Testing environment
- `.env.pat` - Pre-production acceptance testing
- `.env.prod` - Production environment

Each environment file should be created manually based on the `.env.template` and should NOT be committed to your repository.

### OAuth Providers

To configure OAuth providers, you'll need to:

1. **Google OAuth**:
    - Go to [Google Developer Console](https://console.developers.google.com/)
    - Create a new project
    - Set up OAuth consent screen
    - Create OAuth credentials
    - Set authorized redirect URIs to:
        - `http://localhost:8080/login/oauth2/code/google` (for development)
        - `https://your-production-domain.com/login/oauth2/code/google` (for production)
    - Add the client ID and secret to your `.env.{environment}` file

2. **Other OAuth Providers**:
    - Uncomment and configure the relevant sections in `application.yaml`
    - Follow similar steps to create OAuth apps and obtain credentials

### Email Configuration

For email verification and password reset functionality:

1. **Gmail** (for development):
    - Enable "Less secure apps" or create an App Password
    - Update EMAIL_* variables in your `.env.{environment}` file

2. **SMTP Server** (for production):
    - Configure your SMTP server details
    - Update EMAIL_* variables in your `.env.{environment}` file

### Database Configuration

The template uses PostgreSQL by default (schema managed by Flyway migrations):

1. **Development**:
    - Create a local PostgreSQL database
    - Update DB_* variables in your `.env.{environment}` file

2. **Production**:
    - Configure a production-grade PostgreSQL database
    - Update DB_* variables in your `.env.{environment}` file
    - Consider securing your database connection

## 🧩 Project Structure

### Key Packages

- `config`: Configuration classes for Spring, Security, JWT, etc.
- `controller`: REST controllers for all endpoints
- `dto`: Data Transfer Objects for API requests/responses
- `entities`: JPA entities for database models
- `enums`: Enum definitions (Role, AuthProvider, etc.)
- `repositories`: Spring Data JPA repositories
- `security`: Security-related classes (JWT, authentication)
- `service`: Business logic services
- `validation`: Validation utilities and error handlers
- `aspect`: Aspect-oriented programming components (for auditing)
- `filter`: HTTP filters (rate limiting, authentication)
- `annotation`: Custom annotations
- `health`: Custom health indicators

### Key Features Implementation

#### Authentication and Security

- `JwtAuthenticationFilter`: Handles JWT authentication
- `JwtTokenProvider`: Manages JWT token generation and validation
- `RefreshTokenService`: Handles refresh tokens for JWT renewal
- `AuthService`: Core authentication service
- `SecurityConfig`: Security configuration

#### User Management

- `UserService`: User management functionality
- `ProfileService`: User profile management
- `ProfileController`: API endpoints for profile management

#### Email Operations

- `EmailService`: Sends verification and password reset emails
- Email templates in `src/main/resources/templates/email/`

#### Internationalization

- `MessageService`: Access to internationalized messages
- `LanguageController`: API for changing language
- Message properties in `src/main/resources/i18n/`

#### Audit and Metrics

- `AuditService`: Records security and system events
- `AuditAspect`: AOP for automatic method auditing
- `MetricsService`: Records application metrics
- `RateLimitService`: Enforces rate limiting policies

## 🐳 Docker Deployment

The project includes comprehensive Docker support:

```bash
# Development environment
docker-compose -f docker/compose/docker-compose.yml -f docker/compose/docker-compose.dev.yml up -d

# Test environment
docker-compose -f docker/compose/docker-compose.yml -f docker/compose/docker-compose.test.yml up

# PAT (Pre-production) environment
docker-compose -f docker/compose/docker-compose.yml -f docker/compose/docker-compose.pat.yml up -d

# Production environment
docker-compose -f docker/compose/docker-compose.yml -f docker/compose/docker-compose-prod.yml up -d
```

## ☁️ Azure Deployment

The template includes Azure DevOps pipeline configurations and infrastructure code:

1. **ARM Template**:
    - `azure/arm-template.json` defines the infrastructure
    - Run `azure/deploy-infrastructure.ps1` to provision resources

2. **Azure DevOps Pipeline**:
    - `azure-pipelines.yml` provides CI/CD configurations
    - Run `azure/setup-azure-devops-variables.ps1` to set up pipeline variables

3. **Container Registry**:
    - Use `azure/build-and-push-image.ps1` to build and push Docker images

## 🧪 Testing

The project includes a comprehensive test suite:

- Unit tests for services and components (Surefire — no database needed)
- Integration tests (`*IT`) for end-to-end functionality (Failsafe — run a real PostgreSQL)
- Security tests for authentication flows

Integration tests run against a **real PostgreSQL**, not H2 or Testcontainers (see
`docs/adr/0003-testcontainers-postgres-for-tests.md`). Start a throwaway test DB first:

```bash
docker run -d --name oauth-pg-test \
  -e POSTGRES_DB=oauth_template_test \
  -e POSTGRES_USER=test -e POSTGRES_PASSWORD=test \
  -p 5433:5432 postgres:16-alpine
```

The `test` profile defaults to `jdbc:postgresql://localhost:5433/oauth_template_test`
(override with `TEST_DB_URL` / `TEST_DB_USERNAME` / `TEST_DB_PASSWORD`).

Run unit tests only (no DB required):

```bash
./mvnw test
```

Run the full suite including integration tests (requires the test DB above):

```bash
./mvnw verify
```

Run a specific test:

```bash
./mvnw test -Dtest=UserServiceTest
```

## 🔍 API Documentation

Swagger UI is available at:
- Development: http://localhost:8080/swagger-ui.html
- Production: https://your-domain.com/swagger-ui.html

The API is grouped into functional areas:
- Authentication (auth endpoints)
- User Management (user and profile endpoints)
- Administration (admin and moderator endpoints)

## 🙏 Acknowledgements

- Spring Boot and Spring Security
- OAuth 2.0 providers
- Docker and Docker Compose
- Azure DevOps

---

⚠️ **Security Notice**: Before using this template in production, ensure all security aspects are properly configured, especially:
- JWT secrets should be strong, random, and kept secure
- Database credentials should be properly protected
- OAuth client secrets should be stored securely
- Production deployments should use HTTPS exclusively
- Environment files (`.env.*`) should NEVER be committed to version control
- Use secrets management services (like Azure Key Vault) for production deployments

# No CI/CD for now — drop the Azure pipeline, add no GitHub Actions

> **SUPERSEDED by ADR-0004 (2026-06-25).** The "no CI" stance below is reversed: a GitHub Actions build+test+scan gate now runs on every PR to `dev` and is an enforced required check. The `azure/`-removal revision below still stands. See `0004-github-actions-ci.md`.

The template shipped with Azure DevOps Pipelines (`azure-pipelines.yml`) and Azure infra scripts. Because this is a **personal-dev template that is not deployed anywhere**, we delete `azure-pipelines.yml` and deliberately add **no** GitHub Actions workflow. A reader will reasonably expect CI on a backend repo, so recording the choice: CI/CD is intentionally absent until/unless this template is adopted by a real project that picks a deploy target. This is easy to reverse (add a workflow later), but worth noting so nobody assumes CI was forgotten.

## Revision: the stale `azure/` IaC was removed, not just left in place

ADR-0002 originally kept the `azure/` infra (ARM template + PowerShell) in place as "stale but out of scope." During the solid-foundation pass that was reversed: the entire `azure/` directory was **deleted**. It still described a **MySQL** Microsoft.DBforMySQL server and an Azure DevOps variable-group/pipeline model that directly contradicts the PostgreSQL + Jib direction this template now takes, so carrying it was actively misleading rather than merely dormant. A future adopter that wires up Azure should author fresh IaC for their chosen target (Postgres + a registry that consumes the Jib image) rather than converting the old MySQL ARM template.

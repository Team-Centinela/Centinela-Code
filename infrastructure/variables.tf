variable "project" {
  description = "Project slug used in resource naming."
  type        = string
  default     = "centinela"
}

variable "environment" {
  description = "Deployment environment."
  type        = string
  default     = "dev"
}

variable "location" {
  description = "Azure region for all resources."
  type        = string
  default     = "eastus2"
}

variable "subscription_id" {
  description = "Azure subscription id. Prefer ARM_SUBSCRIPTION_ID env var; override here only for non-CI runs."
  type        = string
  default     = null
  sensitive   = true
}

variable "tenant_id" {
  description = "Azure tenant id. Prefer ARM_TENANT_ID env var; override here only for non-CI runs."
  type        = string
  default     = null
  sensitive   = true
}

variable "github_org" {
  description = "GitHub organization that owns the repo; used to scope OIDC federated credentials."
  type        = string
  default     = "Team-Centinela"
}

variable "github_repo" {
  description = "GitHub repository name; used to scope OIDC federated credentials."
  type        = string
  default     = "Centinela-Code"
}

variable "oidc_federated_branch" {
  description = "Branch whose pushes trigger CI apply."
  type        = string
  default     = "develop"
}

# AAD owner / budget recipients. Default is the operator tenant seeded for
# Phase 0 (#274); AAD group creation lives in a follow-up. Override per
# environment via terraform.tfvars when promoting past Phase 0.
variable "aad_owner_email" {
  description = "AAD group email used as the documents-worm owner (ADR-002 §WORM) and as the budget-alert recipient (ADR-007 §7.7)."
  type        = string
  default     = "torreslopezjeronimo@gmail.com"
}

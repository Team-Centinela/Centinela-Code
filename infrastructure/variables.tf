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

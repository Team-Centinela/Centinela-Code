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

variable "aad_owner_email" {
  description = "AAD owner email used as the documents-worm owner (ADR-002 §WORM) and as the budget-alert recipient (ADR-007 §7.7)."
  type        = string
  default     = "torreslopezjeronimo@gmail.com"
}

variable "postgresql_admin_password" {
  description = "Initial AAD-bootstrap administrator password for PostgreSQL Flexible Server. Sensitive; supply via secrets.tfvars."
  type        = string
  sensitive   = true
  default     = null
}

variable "postgresql_aad_administrator_login" {
  description = "AAD UPN/display name granted PostgreSQL AAD admin (break-glass)."
  type        = string
  default     = ""
}

variable "postgresql_aad_administrator_object_id" {
  description = "AAD object id of the administrator login above. Empty string defaults to the Terraform runner's principal id."
  type        = string
  default     = ""
}

variable "postgresql_extensions" {
  description = "Allowlist of PostgreSQL extensions to enable on the B1ms Flexible Server."
  type        = list(string)
  default     = ["postgis", "uuid-ossp", "pg_stat_statements"]
}

variable "ingestion_image" {
  description = "Fully qualified ingestion image reference (ACR login server / repo : tag)."
  type        = string
  default     = ""
}

variable "serverless_engine_image" {
  description = "Fully qualified serverless-engine image reference."
  type        = string
  default     = ""
}

variable "core_backend_image" {
  description = "Fully qualified core-backend image reference."
  type        = string
  default     = ""
}

variable "ocr_worker_enabled" {
  description = "Whether to provision the ocr-worker ACA app. Default false; only enable once OCR research confirms deployable code exists."
  type        = bool
  default     = false
}

variable "ocr_worker_image" {
  description = "Fully qualified OCR worker image reference. Required only when ocr_worker_enabled = true."
  type        = string
  default     = null
}

variable "document_intelligence_endpoint" {
  description = "Azure AI Document Intelligence endpoint. Required only when ocr_worker_enabled = true."
  type        = string
  default     = null
}

variable "budget_alert_emails" {
  description = "Email recipients for budget notifications at 50/80/90/100 percent thresholds (ADR-007 §7.7)."
  type        = list(string)
  default     = []
}

variable "teams_webhook_url" {
  description = "Microsoft Teams incoming webhook URL. Optional; if null, Teams channel is skipped."
  type        = string
  sensitive   = true
  default     = null
}

variable "worm_owner_group_principal" {
  description = "AAD group principal (object id) that owns the WORM immutability policy."
  type        = string
  default     = ""
}

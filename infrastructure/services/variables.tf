variable "project" {
  description = "Project slug used in resource naming."
  type        = string
}

variable "environment" {
  description = "Deployment environment."
  type        = string
}

variable "location" {
  description = "Azure region for all resources."
  type        = string
}

variable "resource_group_name" {
  description = "Resource group in which all services live."
  type        = string
}

variable "suffix" {
  description = "Short deterministic suffix (6 chars) derived from subscription id, used to make globally-unique Azure resource names deterministic."
  type        = string
}

variable "common_tags" {
  description = "Common tags applied to every Azure resource (Mode (a) baseline)."
  type        = map(string)
  default     = {}
}

variable "mode_b_tags" {
  description = "Mode (b) centinela:* tags per ADR-010 §10.5. Applied to Tier-1 platform resources (RG, ACR, Service Bus, ACA Env, Key Vault, App Insights, Storage Account)."
  type        = map(string)
  default     = {}
}

variable "lane_tags" {
  description = "Per-lane Mode (b) centinela:* tags applied to each Container App."
  type        = map(map(string))
  default     = {}
}

variable "postgresql_admin_password" {
  description = "Initial AAD-bootstrap administrator password. PostgreSQL is configured AAD-only; this is retained only for emergency break-glass. Sensitive."
  type        = string
  sensitive   = true
  default     = null

  validation {
    condition     = var.postgresql_admin_password == null || length(var.postgresql_admin_password) >= 12
    error_message = "postgresql_admin_password must be at least 12 characters when provided."
  }
}

variable "postgresql_aad_administrator_login" {
  description = "AAD UPN/display name granted PostgreSQL AAD admin (break-glass)."
  type        = string
}

variable "postgresql_aad_administrator_object_id" {
  description = "AAD object id of the administrator login above. Defaults to the Terraform runner's principal id when empty."
  type        = string
  default     = ""
}

variable "postgresql_extensions" {
  description = "Allowlist of PostgreSQL extensions to enable on the B1ms Flexible Server (per ADR-002)."
  type        = list(string)
  default     = ["postgis", "uuid-ossp", "pg_stat_statements"]
}

variable "acr_sku" {
  description = "ACR SKU. Basic is sufficient for the 21-day window (ADR-002, ADR-009)."
  type        = string
  default     = "Basic"

  validation {
    condition     = contains(["Basic", "Standard", "Premium"], var.acr_sku)
    error_message = "ACR SKU must be one of Basic, Standard, Premium."
  }
}

variable "ingestion_image" {
  description = "Fully qualified ingestion image reference (e.g. acrcentineladevXXXXXX.azurecr.io/centinela-ingestion:0.0.1-SNAPSHOT)."
  type        = string
}

variable "serverless_engine_image" {
  description = "Fully qualified serverless engine image reference."
  type        = string
}

variable "core_backend_image" {
  description = "Fully qualified core-backend image reference."
  type        = string
}

variable "ocr_worker_enabled" {
  description = "Whether to provision the ocr-worker ACA app. Default false; only enable once OCR research confirms deployable code exists (services/ocr-worker currently ships README only)."
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
  description = "Email recipients for budget notifications at 50/80/90/100 percent thresholds (per ADR-007 §7.7)."
  type        = list(string)
  default     = []
}

variable "teams_webhook_url" {
  description = "Microsoft Teams incoming webhook URL. Optional; if null, Teams channel is skipped. Sensitive."
  type        = string
  sensitive   = true
  default     = null
}

variable "worm_owner_group_principal" {
  description = "AAD group principal (object id) that owns the WORM immutability policy (per ADR-002 §WORM policy)."
  type        = string
  default     = ""
}

variable "ocr_worker_min_replicas" {
  description = "Min replicas for the OCR worker when enabled. Default 0 (scale to zero)."
  type        = number
  default     = 0
}

variable "aca_environment_infrastructure_subnet_id" {
  description = "Optional subnet id for ACA Environment VNet integration. Leave null to skip VNet integration (Consumption plan default)."
  type        = string
  default     = null
}

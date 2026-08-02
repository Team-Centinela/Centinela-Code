resource "azurerm_resource_group" "main" {
  name     = local.rg_name
  location = var.location

  tags = merge(local.common_tags, local.mode_b_tags)
}

module "services" {
  source = "./services"

  project             = var.project
  environment         = var.environment
  location            = var.location
  resource_group_name = azurerm_resource_group.main.name
  suffix              = local.suffix

  common_tags = local.common_tags
  mode_b_tags = local.mode_b_tags
  lane_tags   = local.lane_tags

  postgresql_admin_password              = var.postgresql_admin_password
  postgresql_aad_administrator_login     = var.postgresql_aad_administrator_login
  postgresql_aad_administrator_object_id = var.postgresql_aad_administrator_object_id
  postgresql_extensions                  = var.postgresql_extensions

  ingestion_image         = var.ingestion_image
  serverless_engine_image = var.serverless_engine_image
  core_backend_image      = var.core_backend_image

  ocr_worker_enabled             = var.ocr_worker_enabled
  ocr_worker_image               = var.ocr_worker_image
  document_intelligence_endpoint = var.document_intelligence_endpoint

  budget_alert_emails = var.budget_alert_emails
  teams_webhook_url   = var.teams_webhook_url

  worm_owner_group_principal = var.worm_owner_group_principal
}

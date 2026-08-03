locals {
  log_analytics_name    = "log-${var.project}-${var.environment}"
  app_insights_name     = "appi-${var.project}-${var.environment}"
  acr_name              = "acr${var.project}${var.environment}${var.suffix}"
  acr_login_server      = "${local.acr_name}.azurecr.io"
  key_vault_name        = "kv-${var.project}-${var.environment}-${var.suffix}"
  storage_account_name  = "st${var.project}${var.environment}${var.suffix}"
  postgres_name         = "psql-${var.project}-${var.environment}"
  service_bus_namespace = "sb-${var.project}-${var.environment}"
  aca_environment_name  = "cae-${var.project}-${var.environment}"
  budget_name           = "centinela-21day-budget"
  action_group_name     = "ag-${var.project}-${var.environment}-budget"

  storage_documents_container = "documents-worm"

  service_bus_topics = {
    "transactions-raw" = {
      subscriptions = ["serverless-engine", "core-backend"]
    }
    "case-events" = {
      subscriptions = ["reporting-updates", "alerts"]
    }
  }

  service_bus_queues = ["documents-pending"]

  poison_entity_names = concat(
    flatten([
      for topic, cfg in local.service_bus_topics : [
        for sub in cfg.subscriptions : "${topic}-${sub}-poison"
      ]
    ]),
    [for q in local.service_bus_queues : "${q}-poison"],
  )

  ingestion_port         = 8081
  serverless_engine_port = 8082
  core_backend_port      = 8080
  ocr_worker_port        = 8000

  http_scale_rule_default = {
    name                = "http-requests"
    concurrent_requests = "50"
  }
}

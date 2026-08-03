resource "azurerm_container_registry" "main" {
  name                = local.acr_name
  resource_group_name = var.resource_group_name
  location            = var.location
  sku                 = var.acr_sku
  admin_enabled       = false

  tags = merge(var.common_tags, var.mode_b_tags)
}

locals {
  postgres_jdbc_url = "jdbc:postgresql://${azurerm_postgresql_flexible_server.main.fqdn}:5432/${azurerm_postgresql_flexible_server_database.centinela.name}"

  postgres_env_ingestion = {
    SPRING_DATASOURCE_URL = local.postgres_jdbc_url
    DB_USER               = var.postgresql_aad_administrator_login
  }

  postgres_env_serverless_engine = {
    SPRING_DATASOURCE_URL = local.postgres_jdbc_url
    DB_USER               = var.postgresql_aad_administrator_login
  }

  postgres_env_core_backend = {
    DB_HOST = azurerm_postgresql_flexible_server.main.fqdn
    DB_PORT = "5432"
    DB_NAME = azurerm_postgresql_flexible_server_database.centinela.name
    DB_USER = var.postgresql_aad_administrator_login
  }

  ready_aca_apps = {
    ingestion         = azurerm_container_app.ingestion
    serverless-engine = azurerm_container_app.serverless_engine
    core-backend      = azurerm_container_app.core_backend
  }

  servicebus_secret_name        = "servicebus-connection-string"
  appinsights_secret_name       = "appinsights-connection-string"
  keda_trigger_param_connection = "connection"
}

resource "azurerm_container_app" "ingestion" {
  name                         = "centinela-ingestion"
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = var.resource_group_name
  revision_mode                = "Single"

  identity {
    type = "SystemAssigned"
  }

  registry {
    server   = azurerm_container_registry.main.login_server
    identity = "SystemAssigned"
  }

  template {
    min_replicas = 0
    max_replicas = 5

    container {
      name   = "centinela-ingestion"
      image  = var.ingestion_image
      cpu    = 0.5
      memory = "1Gi"

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "prod"
      }
      env {
        name  = "SPRING_CLOUD_STREAM_ENABLED"
        value = "true"
      }
      env {
        name        = "AZURE_SERVICEBUS_CONNECTION_STRING"
        secret_name = local.servicebus_secret_name
      }
      env {
        name        = "APPLICATIONINSIGHTS_CONNECTION_STRING"
        secret_name = local.appinsights_secret_name
      }

      dynamic "env" {
        for_each = local.postgres_env_ingestion
        content {
          name  = env.key
          value = env.value
        }
      }

      liveness_probe {
        transport = "HTTP"
        path      = "/actuator/health/liveness"
        port      = local.ingestion_port
      }
      readiness_probe {
        transport = "HTTP"
        path      = "/actuator/health/readiness"
        port      = local.ingestion_port
      }
    }

    http_scale_rule {
      name                = local.http_scale_rule_default.name
      concurrent_requests = local.http_scale_rule_default.concurrent_requests
    }
  }

  ingress {
    external_enabled           = true
    target_port                = local.ingestion_port
    transport                  = "auto"
    allow_insecure_connections = false

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  secret {
    name                = local.servicebus_secret_name
    key_vault_secret_id = azurerm_key_vault_secret.servicebus_connection_string.versionless_id
    identity            = "SystemAssigned"
  }
  secret {
    name                = local.appinsights_secret_name
    key_vault_secret_id = azurerm_key_vault_secret.appinsights_connection_string.versionless_id
    identity            = "SystemAssigned"
  }

  tags = merge(var.common_tags, lookup(var.lane_tags, "ingestion", var.mode_b_tags))
}

resource "azurerm_container_app" "serverless_engine" {
  name                         = "centinela-serverless-engine"
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = var.resource_group_name
  revision_mode                = "Single"

  identity {
    type = "SystemAssigned"
  }

  registry {
    server   = azurerm_container_registry.main.login_server
    identity = "SystemAssigned"
  }

  template {
    min_replicas = 0
    max_replicas = 10

    container {
      name   = "centinela-serverless-engine"
      image  = var.serverless_engine_image
      cpu    = 0.5
      memory = "1Gi"

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "prod"
      }
      env {
        name  = "SPRING_CLOUD_STREAM_ENABLED"
        value = "true"
      }
      env {
        name  = "AZURE_SERVICEBUS_CONSUMER_GROUP"
        value = "serverless-engine"
      }
      env {
        name        = "AZURE_SERVICEBUS_CONNECTION_STRING"
        secret_name = local.servicebus_secret_name
      }
      env {
        name        = "APPLICATIONINSIGHTS_CONNECTION_STRING"
        secret_name = local.appinsights_secret_name
      }

      dynamic "env" {
        for_each = local.postgres_env_serverless_engine
        content {
          name  = env.key
          value = env.value
        }
      }

      liveness_probe {
        transport = "HTTP"
        path      = "/actuator/health/liveness"
        port      = local.serverless_engine_port
      }
      readiness_probe {
        transport = "HTTP"
        path      = "/actuator/health/readiness"
        port      = local.serverless_engine_port
      }
    }

    custom_scale_rule {
      name             = "service-bus-topic-subscription"
      custom_rule_type = "azure-servicebus"
      metadata = {
        topicName        = "transactions-raw"
        subscriptionName = "serverless-engine"
        messageCount     = "10"
        namespace        = azurerm_servicebus_namespace.main.name
      }

      authentication {
        secret_name       = local.servicebus_secret_name
        trigger_parameter = local.keda_trigger_param_connection
      }
    }
  }

  ingress {
    external_enabled           = true
    target_port                = local.serverless_engine_port
    transport                  = "auto"
    allow_insecure_connections = false

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  secret {
    name                = local.servicebus_secret_name
    key_vault_secret_id = azurerm_key_vault_secret.servicebus_connection_string.versionless_id
    identity            = "SystemAssigned"
  }
  secret {
    name                = local.appinsights_secret_name
    key_vault_secret_id = azurerm_key_vault_secret.appinsights_connection_string.versionless_id
    identity            = "SystemAssigned"
  }

  tags = merge(var.common_tags, lookup(var.lane_tags, "serverless-engine", var.mode_b_tags))
}

resource "azurerm_container_app" "core_backend" {
  name                         = "centinela-core-backend"
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = var.resource_group_name
  revision_mode                = "Single"

  identity {
    type = "SystemAssigned"
  }

  registry {
    server   = azurerm_container_registry.main.login_server
    identity = "SystemAssigned"
  }

  template {
    min_replicas = 0
    max_replicas = 5

    container {
      name   = "centinela-core-backend"
      image  = var.core_backend_image
      cpu    = 0.5
      memory = "1Gi"

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "prod"
      }
      env {
        name  = "SPRING_CLOUD_STREAM_ENABLED"
        value = "true"
      }
      env {
        name        = "AZURE_SERVICEBUS_CONNECTION_STRING"
        secret_name = local.servicebus_secret_name
      }
      env {
        name        = "APPLICATIONINSIGHTS_CONNECTION_STRING"
        secret_name = local.appinsights_secret_name
      }

      dynamic "env" {
        for_each = local.postgres_env_core_backend
        content {
          name  = env.key
          value = env.value
        }
      }

      liveness_probe {
        transport = "HTTP"
        path      = "/actuator/health/liveness"
        port      = local.core_backend_port
      }
      readiness_probe {
        transport = "HTTP"
        path      = "/actuator/health/readiness"
        port      = local.core_backend_port
      }
    }

    http_scale_rule {
      name                = local.http_scale_rule_default.name
      concurrent_requests = local.http_scale_rule_default.concurrent_requests
    }
  }

  ingress {
    external_enabled           = true
    target_port                = local.core_backend_port
    transport                  = "auto"
    allow_insecure_connections = false

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  secret {
    name                = local.servicebus_secret_name
    key_vault_secret_id = azurerm_key_vault_secret.servicebus_connection_string.versionless_id
    identity            = "SystemAssigned"
  }
  secret {
    name                = local.appinsights_secret_name
    key_vault_secret_id = azurerm_key_vault_secret.appinsights_connection_string.versionless_id
    identity            = "SystemAssigned"
  }

  tags = merge(var.common_tags, lookup(var.lane_tags, "core-backend", var.mode_b_tags))
}

resource "azurerm_container_app" "ocr_worker" {
  count = var.ocr_worker_enabled ? 1 : 0

  name                         = "centinela-ocr-worker"
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = var.resource_group_name
  revision_mode                = "Single"

  identity {
    type = "SystemAssigned"
  }

  registry {
    server   = azurerm_container_registry.main.login_server
    identity = "SystemAssigned"
  }

  template {
    min_replicas = var.ocr_worker_min_replicas
    max_replicas = 3

    container {
      name   = "centinela-ocr-worker"
      image  = var.ocr_worker_image
      cpu    = 0.5
      memory = "1Gi"

      env {
        name        = "AZURE_SERVICEBUS_CONNECTION_STRING"
        secret_name = local.servicebus_secret_name
      }
      env {
        name  = "DOCUMENT_INTELLIGENCE_ENDPOINT"
        value = var.document_intelligence_endpoint
      }
    }

    custom_scale_rule {
      name             = "service-bus-queue-length"
      custom_rule_type = "azure-servicebus"
      metadata = {
        queueName    = "documents-pending"
        messageCount = "10"
        namespace    = azurerm_servicebus_namespace.main.name
      }

      authentication {
        secret_name       = local.servicebus_secret_name
        trigger_parameter = local.keda_trigger_param_connection
      }
    }
  }

  secret {
    name                = local.servicebus_secret_name
    key_vault_secret_id = azurerm_key_vault_secret.servicebus_connection_string.versionless_id
    identity            = "SystemAssigned"
  }

  tags = merge(var.common_tags, lookup(var.lane_tags, "ocr-worker", var.mode_b_tags))
}

resource "azurerm_role_assignment" "acr_pull" {
  for_each = local.ready_aca_apps

  scope                = azurerm_container_registry.main.id
  role_definition_name = "AcrPull"
  principal_id         = each.value.identity[0].principal_id
}

resource "azurerm_role_assignment" "acr_pull_ocr_worker" {
  count = var.ocr_worker_enabled ? 1 : 0

  scope                = azurerm_container_registry.main.id
  role_definition_name = "AcrPull"
  principal_id         = azurerm_container_app.ocr_worker[0].identity[0].principal_id
}

resource "azurerm_role_assignment" "kv_secrets_user" {
  for_each = local.ready_aca_apps

  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = each.value.identity[0].principal_id
}

resource "azurerm_role_assignment" "kv_secrets_user_ocr_worker" {
  count = var.ocr_worker_enabled ? 1 : 0

  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_container_app.ocr_worker[0].identity[0].principal_id
}

locals {
  ingestion_principal_id      = azurerm_container_app.ingestion.identity[0].principal_id
  serverless_engine_principal = azurerm_container_app.serverless_engine.identity[0].principal_id
  core_backend_principal      = azurerm_container_app.core_backend.identity[0].principal_id
  ocr_worker_principal        = var.ocr_worker_enabled ? azurerm_container_app.ocr_worker[0].identity[0].principal_id : null

  ingestion_servicebus_duties = {
    topic_senders = {
      "transactions-raw" = azurerm_servicebus_topic.topics["transactions-raw"].id
    }
  }

  serverless_engine_servicebus_duties = {
    topic_senders = {
      "case-events" = azurerm_servicebus_topic.topics["case-events"].id
    }
    topic_receivers = {
      "transactions-raw/serverless-engine" = azurerm_servicebus_subscription.subscriptions["transactions-raw-serverless-engine"].id
    }
  }

  core_backend_servicebus_duties = {
    topic_receivers = {
      "transactions-raw/core-backend" = azurerm_servicebus_subscription.subscriptions["transactions-raw-core-backend"].id
    }
  }
}

resource "azurerm_role_assignment" "servicebus_topic_sender_ingestion" {
  for_each = local.ingestion_servicebus_duties.topic_senders

  scope                = each.value
  role_definition_name = "Azure Service Bus Data Sender"
  principal_id         = local.ingestion_principal_id
}

resource "azurerm_role_assignment" "servicebus_topic_sender_serverless_engine" {
  for_each = local.serverless_engine_servicebus_duties.topic_senders

  scope                = each.value
  role_definition_name = "Azure Service Bus Data Sender"
  principal_id         = local.serverless_engine_principal
}

resource "azurerm_role_assignment" "servicebus_subscription_receiver_serverless_engine" {
  for_each = local.serverless_engine_servicebus_duties.topic_receivers

  scope                = each.value
  role_definition_name = "Azure Service Bus Data Receiver"
  principal_id         = local.serverless_engine_principal
}

resource "azurerm_role_assignment" "servicebus_subscription_receiver_core_backend" {
  for_each = local.core_backend_servicebus_duties.topic_receivers

  scope                = each.value
  role_definition_name = "Azure Service Bus Data Receiver"
  principal_id         = local.core_backend_principal
}

resource "azurerm_role_assignment" "servicebus_queue_receiver_ocr_worker" {
  count = var.ocr_worker_enabled ? 1 : 0

  scope                = azurerm_servicebus_queue.queues["documents-pending"].id
  role_definition_name = "Azure Service Bus Data Receiver"
  principal_id         = local.ocr_worker_principal
}

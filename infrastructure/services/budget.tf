resource "azurerm_monitor_action_group" "budget" {
  name                = local.action_group_name
  resource_group_name = var.resource_group_name
  short_name          = "centinela"
  location            = var.location

  dynamic "email_receiver" {
    for_each = var.budget_alert_emails
    content {
      name                    = replace(email_receiver.value, "@", "-at-")
      email_address           = email_receiver.value
      use_common_alert_schema = true
    }
  }

  dynamic "webhook_receiver" {
    for_each = var.teams_webhook_url == null ? [] : [var.teams_webhook_url]
    content {
      name                    = "teams-incoming-webhook"
      service_uri             = webhook_receiver.value
      use_common_alert_schema = true
    }
  }

  tags = merge(var.common_tags, var.mode_b_tags)
}

resource "azurerm_consumption_budget_resource_group" "main" {
  name              = local.budget_name
  resource_group_id = "/subscriptions/${data.azurerm_client_config.current.subscription_id}/resourceGroups/${var.resource_group_name}"

  amount     = 60
  time_grain = "Monthly"

  time_period {
    start_date = "2026-07-01T00:00:00Z"
    end_date   = "2026-08-31T23:59:59Z"
  }

  notification {
    enabled        = true
    threshold      = 50
    operator       = "GreaterThan"
    contact_emails = var.budget_alert_emails
    contact_groups = [azurerm_monitor_action_group.budget.id]
  }

  notification {
    enabled        = true
    threshold      = 80
    operator       = "GreaterThan"
    contact_emails = var.budget_alert_emails
    contact_groups = [azurerm_monitor_action_group.budget.id]
  }

  notification {
    enabled        = true
    threshold      = 90
    operator       = "GreaterThan"
    contact_emails = var.budget_alert_emails
    contact_groups = [azurerm_monitor_action_group.budget.id]
  }

  notification {
    enabled        = true
    threshold      = 100
    operator       = "GreaterThan"
    contact_emails = var.budget_alert_emails
    contact_groups = [azurerm_monitor_action_group.budget.id]
  }
}

resource "azurerm_servicebus_namespace" "main" {
  name                = local.service_bus_namespace
  resource_group_name = var.resource_group_name
  location            = var.location
  sku                 = "Standard"

  tags = merge(var.common_tags, var.mode_b_tags)
}

resource "azurerm_servicebus_topic" "topics" {
  for_each = local.service_bus_topics

  name         = each.key
  namespace_id = azurerm_servicebus_namespace.main.id

  partitioning_enabled       = true
  batched_operations_enabled = true
}

resource "azurerm_servicebus_subscription" "subscriptions" {
  for_each = {
    for pair in flatten([
      for topic, cfg in local.service_bus_topics : [
        for sub in cfg.subscriptions : {
          key      = "${topic}-${sub}"
          topic    = topic
          sub_name = sub
        }
      ]
    ]) : pair.key => pair
  }

  name                                 = each.value.sub_name
  topic_id                             = azurerm_servicebus_topic.topics[each.value.topic].id
  max_delivery_count                   = 3
  lock_duration                        = "PT1M"
  default_message_ttl                  = "P14D"
  dead_lettering_on_message_expiration = true

  forward_dead_lettered_messages_to = "${each.value.topic}-${each.value.sub_name}-poison"
}

resource "azurerm_servicebus_queue" "queues" {
  for_each = toset(local.service_bus_queues)

  name                                 = each.value
  namespace_id                         = azurerm_servicebus_namespace.main.id
  max_delivery_count                   = 3
  lock_duration                        = "PT1M"
  default_message_ttl                  = "P14D"
  dead_lettering_on_message_expiration = true
  partitioning_enabled                 = true
  batched_operations_enabled           = true

  forward_dead_lettered_messages_to = "${each.value}-poison"
}

resource "azurerm_servicebus_queue" "poison" {
  for_each = toset(local.poison_entity_names)

  name                                 = each.value
  namespace_id                         = azurerm_servicebus_namespace.main.id
  lock_duration                        = "PT1M"
  default_message_ttl                  = "P14D"
  dead_lettering_on_message_expiration = false
  partitioning_enabled                 = true
}

data "azurerm_client_config" "current" {}

locals {
  rg_name = "rg-${var.project}-${var.environment}"

  suffix = substr(replace(data.azurerm_client_config.current.subscription_id, "-", ""), 0, 6)

  common_tags = {
    project       = var.project
    environment   = var.environment
    managed_by    = "terraform"
    cost_center   = "centinela-platform"
    owner         = var.aad_owner_email
    documentation = "../docs/architecture/06-technology-stack.md"
  }

  mode_b_tags = {
    "centinela:lp"     = "lane-e"
    "centinela:epic"   = "[E.0]"
    "centinela:issue"  = "[E.A0] #274"
    "centinela:sprint" = "sprint-1"
    "centinela:start"  = "2026-07-31"
    "centinela:close"  = "pending"
    "centinela:action" = "create"
  }

  ingestion_lane_tags = {
    "centinela:lp"     = "lane-b"
    "centinela:epic"   = "[B.1] #135"
    "centinela:issue"  = "[B.A1] #141"
    "centinela:sprint" = "sprint-1"
    "centinela:start"  = "2026-07-31"
    "centinela:close"  = "pending"
    "centinela:action" = "create"
  }

  serverless_engine_lane_tags = {
    "centinela:lp"     = "lane-c"
    "centinela:epic"   = "[C.1] #136"
    "centinela:issue"  = "[C.A1] #142"
    "centinela:sprint" = "sprint-1"
    "centinela:start"  = "2026-07-31"
    "centinela:close"  = "pending"
    "centinela:action" = "create"
  }

  core_backend_lane_tags = {
    "centinela:lp"     = "lane-d"
    "centinela:epic"   = "[D.1] #138"
    "centinela:issue"  = "[D.A1] #147"
    "centinela:sprint" = "sprint-1"
    "centinela:start"  = "2026-07-31"
    "centinela:close"  = "pending"
    "centinela:action" = "create"
  }

  ocr_worker_lane_tags = {
    "centinela:lp"     = "lane-e"
    "centinela:epic"   = "[E.0] #137"
    "centinela:issue"  = "[E.A0] #274"
    "centinela:sprint" = "sprint-1"
    "centinela:start"  = "2026-07-31"
    "centinela:close"  = "pending"
    "centinela:action" = "create"
  }

  lane_tags = {
    ingestion         = local.ingestion_lane_tags
    serverless-engine = local.serverless_engine_lane_tags
    core-backend      = local.core_backend_lane_tags
    ocr-worker        = local.ocr_worker_lane_tags
  }
}

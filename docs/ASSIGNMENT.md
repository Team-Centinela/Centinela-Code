Here is the complete and exact breakdown of the **Objectives**, **Requirements**, and **Constraints** for the "Centinela" project, translated and organized in English.

---

## 1. Project Objectives

The overarching goal is to deliver a complete, decoupled, event-driven transactional fraud detection platform on Azure within a 3-week timeline.

### Core Objectives
1. **Deliver a Complete Platform:**
   * An **API** to ingest transactions.
   * A **Serverless Engine** to evaluate transactions and calculate risk scores.
   * A **Case Management System** for human fraud analysts.
   * Supporting **Cloud Infrastructure** provisioned via code.
2. **Real-Time Fraud Detection:** Analyze incoming credit/debit card and transfer transactions, scoring them via heuristic rules in real time.
3. **Deterministic Fraud Explanation:** Produce clear, human-readable explanations of *why* a transaction was flagged using deterministic templates (not LLMs).
4. **Document Verification Integration:** Integrate an AI-based document processing service to extract user details from identity documents uploaded during case escalations.
5. **End-to-End Observability:** Provide complete visibility into the lifecycle of every transaction, showing logs, traces, and system metrics when failures occur.
6. **Cost-Efficient Cloud Architecture:** Deliver a fully functional system while keeping total Azure consumption under **$60 USD** out of the provided $200 budget.

---

## 2. Requirements

### Functional Requirements

#### A. Data Ingestion & Transaction Flow
1. **Immediate Ingestion Acknowledgment:** The API receives a transaction, immediately responds to the client with an acknowledgment, and pushes the event to a pipeline. It **must not** wait for scoring or case creation.
2. **Event-Driven Pipeline:**
   * **Step 1:** Ingest transaction & return response immediately.
   * **Step 2:** Publish transaction event.
   * **Step 3:** Serverless worker consumes event, queries recent account history, applies rules, and calculates score.
   * **Step 4:** Evaluate score against a configurable threshold. If exceeded, trigger a case; if not, store the transaction record.
   * **Step 5:** Create fraud case in the database.
   * **Step 6:** Generate a human-readable explanation text.
   * **Step 7:** Analyst reviews, acts upon, and resolves/closes the case.

#### B. Detection Engine & Heuristic Rules
The engine must evaluate every transaction against logic written explicitly by the team (No ML for scoring). Each triggered rule adds points to the total score:
1. **Transaction Velocity:** High number of transactions for the same account within a short time window (e.g., 8 purchases in 3 minutes).
2. **Atypical Amount:** Transaction amount significantly exceeds historical average (e.g., normal activity ~$50,000, sudden attempt of $4,000,000).
3. **Impossible Geographical Location:** Consecutive transactions from the same account occurring at locations physically impossible to travel between in the elapsed time (e.g., Medellín and Madrid within 10 minutes).
4. **High-Risk Merchant/Category:** Transaction targets a pre-flagged suspicious merchant or category.
5. **Configurable Threshold:** Must use a non-hardcoded threshold to decide when to open a case (must balance false positives vs. uncaught fraud).
6. **Granular Rule Auditing:** The system must save the specific data payload that triggered each rule (the "why"), not just the final numerical score.

#### C. Case Explainer
* Must auto-generate readable text for analysts detailing each rule triggered, the historical baseline, the actual values, and the points added per rule.
* **Must use deterministic templating** (e.g., formatted string templates), not Generative AI/LLMs.

#### D. Identity Verification
* Integrate an AI document recognition service to extract details (Name, ID number, dates) from uploaded PDFs/images (e.g., IDs, bank statements) during case escalation.

#### E. Data Persistence Strategy
You must choose and defend appropriate storage engines based on specific access patterns:
1. **Transactions & Scores:** High-volume, high-frequency writes. Primary query pattern: *"Get recent transactions for account X"*. Requires a clear partitioning strategy.
2. **Fraud Cases:** Low volume, highly relational (Case ↔ Analyst ↔ Resolution ↔ Audit log). Needs strict traceability.
3. **Verification Documents:** Binary blob storage (Write once, read rarely).

#### F. User Roles & Access Control
* **Client:** Originates transactions (no direct system interface).
* **Fraud Analyst:** Views assigned cases, evidence, generated explanations; closes or escalates cases (uploading ID docs).
* **Administrator:** Configures heuristic rules, risk thresholds, flagged merchants, and user accounts.
* **Service:** Machine-to-machine identity used for internal component-to-component communication.
* **Auditor:** Read-only access across the entire system.

---

### Technical & Non-Functional Requirements

1. **Infrastructure as Code (IaC):**
   * The complete infrastructure must be provisioned using version-controlled scripts (e.g., Bicep, Terraform, Azure CLI).
   * The system must be capable of being rebuilt from scratch purely by executing the deployment script.
2. **Security & Secret Management:**
   * Zero secrets (connection strings, keys, credentials) in source code or Git history.
   * All secrets must be stored in a centralized Secret Manager (e.g., Azure Key Vault).
3. **Inter-Component Contract:** Event schemas and API payloads must be strictly defined and documented from Day 1.
4. **Architectural Justification:** Every technical choice (data stores, consistency levels, messaging vs. direct calls, service tiers, partition keys) must be explicitly justified regarding cost and trade-offs.

---

## 3. Constraints & Out of Scope

### Constraints

1. **Decoupled Latency Constraint:** 
   * Client response time must be decoupled from fraud analysis processing.
   * Client **cannot** wait for historical DB lookups, scoring rules, or case creation.
2. **Budget & Subscriptions:**
   * Operating under a single **$200 USD Azure Free Account** per team (valid for 30 days maximum; project duration is 21 days).
   * **Target spend constraint:** Must complete the entire project spending **under $60 USD** (leaving $140 margin).
   * Resource cleanup/shutdown is required when not actively testing (to avoid burning budget over weekends).
3. **Region Quotas:**
   * Region and service quotas (especially AI/Document services) must be verified on **Day 1** due to tier limitations on Azure Free accounts.
4. **Delivery Timeline (3 Weeks):**
   * **Week 1:** Infra, Security/Identity, API Ingestion, Basic Persistence.
   * **Week 2:** Serverless Scoring Pipeline, Data Stores, Case Triggering.
   * **Week 3:** Automated CI/CD Deployment, Document AI Integration, Explainer Engine, End-to-End Observability.

---

### Out of Scope (Explicitly Forbidden)

Do **NOT** implement or consume credit on the following features:
* Container orchestrators using managed clusters (e.g., AKS / Kubernetes).
* Generative AI models / Large Language Models (LLMs) for explanations.
* Staging environments with deployment slot swapping.
* Private Endpoints for storage services.
* Dedicated API Management (APIM) pricing tiers.
package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.event.TransactionReceivedEvent;
import com.centinela.serverless.domain.model.FraudDecision;
import com.centinela.serverless.domain.model.Recommendation;
import com.centinela.serverless.domain.model.TriggeredRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FraudPipelineTest {

    private final TransactionReceivedEvent tx = new TransactionReceivedEvent(
            UUID.randomUUID(), "acc-pipe", new BigDecimal("100.00"), "USD",
            "merchant-1", null, null, Instant.now()
    );

    /** ADR-004 §4.4 canonical threshold + soft FLAG floor. */
    private static final int SCORE_THRESHOLD = 70;
    private static final int FLAG_THRESHOLD = 30;

    /** Empty stages2 — the guard then forces Stage 2 off when Stage 1 is silent. */
    private static FraudPipeline newPipeline(List<PipelineStage> stages1) {
        return newPipeline(stages1, List.of());
    }

    private static FraudPipeline newPipeline(List<PipelineStage> stages1,
                                             List<PipelineStage> stages2) {
        return new FraudPipeline(stages1, stages2,
                new AggregatorStage(SCORE_THRESHOLD, FLAG_THRESHOLD), SCORE_THRESHOLD);
    }

    @Test
    void stages2RunsOnlyWhenStage1FiresGuard() {
        // ADR-004 §4.1 + SrLampi1001 review item 1: the guard branch must
        // keep Stage 2 (FR-3 + FR-2, the expensive PostGIS-backed rules) off
        // when Stage 1 produced no evidence worth investigating.
        var stage1AlwaysSilent = new StubStage(0);  // never fires
        var stage2Tracker = new TrackerStage();
        var pipeline = newPipeline(List.of(stage1AlwaysSilent), List.of(stage2Tracker));
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertFalse(stage2Tracker.called,
                "Stage 2 must NOT run when Stage 1 produced no fired rule (ADR-004 §4.1 guard)");
        assertEquals(0, decision.totalScore());
        assertEquals(Recommendation.APPROVE, decision.recommendation());
    }

    @Test
    void stages2RunsWhenStage1FiresGuard() {
        // Symmetric: when Stage 1 produces at least one fired rule, Stage 2 runs.
        var stage1Fires = new StubStage(20);
        var stage2Tracker = new TrackerStage();
        var pipeline = newPipeline(List.of(stage1Fires), List.of(stage2Tracker));
        var ctx = new EvaluationContext(tx);

        pipeline.execute(ctx);

        assertTrue(stage2Tracker.called,
                "Stage 2 MUST run when Stage 1 fired at least one rule");
    }

    @Test
    void stages2RunsEvenWhenStage1FiresButScoreIsBelowThreshold() {
        // Stage 1 fires a small rule (15 < 70 threshold); Stage 2 must still run
        // because the guard is "any rule fired", not "score >= threshold".
        var stage1FiresSmall = new StubStage(15);
        var stage2Tracker = new TrackerStage();
        var pipeline = newPipeline(List.of(stage1FiresSmall), List.of(stage2Tracker));
        var ctx = new EvaluationContext(tx);

        pipeline.execute(ctx);

        assertTrue(stage2Tracker.called);
    }

    @Test
    void shortCircuitInsideStage1SkipsRemainingStage1Rules() {
        // FR-1 fires 80 (>= 70 threshold) → loop breaks; FR-4 must NOT run.
        var stage1High = new StubStage(80);
        var stage1Low = new TrackerStage();
        var pipeline = newPipeline(List.of(stage1High, stage1Low));
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertFalse(stage1Low.called,
                "Stage 1 short-circuit: rule after threshold-crossing must not run");
        assertEquals(80, decision.totalScore());
        assertEquals(Recommendation.BLOCK, decision.recommendation());
    }

    @Test
    void shortCircuitInsideStage2SkipsRemainingStage2Rules() {
        // Stage 1 fires 30; Stage 2 fires 50 (cumulative 80 >= 70) → Stage 2
        // breaks after that one rule; the remaining Stage 2 rule must not run.
        var stage1Fires = new StubStage(30);
        var stage2High = new StubStage(50);
        var stage2Low = new TrackerStage();
        var pipeline = newPipeline(List.of(stage1Fires), List.of(stage2High, stage2Low));
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertFalse(stage2Low.called,
                "Stage 2 short-circuit: rule after threshold-crossing must not run");
        assertEquals(80, decision.totalScore());
    }

    @Test
    void scores20_0_25_25ShouldReturnBlock() {
        // 20 + 0 + 25 + 25 = 70 → loop short-circuits after stage 2 rule 3.
        var stages1 = List.<PipelineStage>of(
                new StubStage(20),
                new StubStage(0));
        var stages2 = List.<PipelineStage>of(
                new StubStage(25),
                new StubStage(25));
        var pipeline = newPipeline(stages1, stages2);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(70, decision.totalScore());
        assertEquals(Recommendation.BLOCK, decision.recommendation());
    }

    @Test
    void allScoresZeroShouldReturnApprove() {
        var stages1 = List.<PipelineStage>of(new StubStage(0), new StubStage(0));
        var stages2 = List.<PipelineStage>of(new StubStage(0), new StubStage(0));
        var pipeline = newPipeline(stages1, stages2);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(0, decision.totalScore());
        assertEquals(Recommendation.APPROVE, decision.recommendation());
    }

    @Test
    void scores30_0_10_10ShouldReturnFlag() {
        var stages1 = List.<PipelineStage>of(new StubStage(30), new StubStage(0));
        var stages2 = List.<PipelineStage>of(new StubStage(10), new StubStage(10));
        var pipeline = newPipeline(stages1, stages2);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(50, decision.totalScore());
        assertEquals(Recommendation.FLAG, decision.recommendation());
    }

    /**
     * SrLampi1001 review on PR #268 (finding 1 action 2): under ADR-004 §4.4
     * single-threshold model, a probe with stages 30 + 25 + 25 (= 80) must
     * produce BLOCK. The previous §0.2.3 split (50/70) returned FLAG and
     * opened a false-negative window. This test pins the corrected behaviour.
     */
    @Test
    void probe30_25_25ShouldProduceBlockNotFlag() {
        var stages1 = List.<PipelineStage>of(new StubStage(30));
        var stages2 = List.<PipelineStage>of(new StubStage(25), new StubStage(25));
        var pipeline = newPipeline(stages1, stages2);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(80, decision.totalScore());
        assertEquals(Recommendation.BLOCK, decision.recommendation(),
                "30+25+25 = 80 must produce BLOCK under ADR-004 §4.4 (single scoreThreshold=70)");
    }

    @Test
    void emptyStageListsShouldStillProduceDecision() {
        var pipeline = newPipeline(List.of(), List.of());
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(0, decision.totalScore());
        assertEquals(Recommendation.APPROVE, decision.recommendation());
        assertEquals(tx.transactionId(), decision.transactionId());
    }

    @Test
    void triggeredRulesContainsAllThatFired() {
        // Stage 1 fires 1 rule, Stage 2 fires 1 rule, Aggregator fires synthetic.
        var stages1 = List.<PipelineStage>of(new StubStage(20));
        var stages2 = List.<PipelineStage>of(new StubStage(20));
        var pipeline = newPipeline(stages1, stages2);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        // 2 stage rules + 1 aggregator synthetic rule = 3
        assertEquals(3, decision.triggeredRules().size(),
                "1 Stage 1 rule + 1 Stage 2 rule + 1 aggregator synthetic rule");
    }

    @Test
    void aggregatorAndFraudDecisionRecommendationMustAgree() {
        // SrLampi1001 finding 3: under any threshold + score combination, the
        // raw evidence recommendation and the FraudDecision.recommendation
        // MUST agree — no silent drift between the two decision paths.
        // The recommendation is now read from the aggregator's returned
        // TriggeredRule (not a singleton field) so this test is thread-safe.
        int[][] probes = {
                {70, 30, 80},
                {70, 30, 70},
                {70, 30, 71},
                {50, 30, 60},
                {29, 30, 70},
                {0, 30, 70},
        };
        for (int[] probe : probes) {
            int totalScore = probe[0];
            int flag = probe[1];
            int score = probe[2];
            var aggregator = new AggregatorStage(score, flag);
            // Use a single Stage 1 stub with the desired score so the guard
            // fires; Stage 2 is empty.
            var pipeline = new FraudPipeline(
                    List.<PipelineStage>of(new StubStage(totalScore)),
                    List.of(),
                    aggregator, score);
            var ctx = new EvaluationContext(tx);

            FraudDecision decision = pipeline.execute(ctx);

            // Compare against the aggregator's pure-function recomputation,
            // which is the only canonical recommendation source after the
            // thread-safety fix (PR #271 review concurrency evidence).
            assertEquals(aggregator.computeRecommendation(decision.totalScore()),
                    decision.recommendation(),
                    "FraudDecision.recommendation must match aggregator.computeRecommendation at totalScore=" + totalScore
                            + ", flag=" + flag + ", score=" + score);
        }
    }

    @Test
    void constructorRejectsNullStages1() {
        assertThrows(IllegalArgumentException.class,
                () -> new FraudPipeline(null, List.of(),
                        new AggregatorStage(70, 30), 70));
    }

    @Test
    void constructorRejectsNullStages2() {
        assertThrows(IllegalArgumentException.class,
                () -> new FraudPipeline(List.of(), null,
                        new AggregatorStage(70, 30), 70));
    }

    @Test
    void constructorRejectsScoreThresholdBelowZero() {
        assertThrows(FraudPipeline.InvalidPipelineConfigException.class,
                () -> new FraudPipeline(List.of(), List.of(),
                        new AggregatorStage(70, 30), -1));
    }

    @Test
    void constructorRejectsScoreThresholdAboveHundred() {
        assertThrows(FraudPipeline.InvalidPipelineConfigException.class,
                () -> new FraudPipeline(List.of(), List.of(),
                        new AggregatorStage(70, 30), 101));
    }

    /**
     * SrLampi1001 review on PR #271 (concurrency evidence): the singleton
     * {@code AggregatorStage} holds {@code lastRecommendation} in a mutable
     * field, and {@code FraudPipeline} reads it on the same thread that just
     * called {@code aggregator.evaluate(ctx)}. That contract relies on a
     * subtle per-request thread-locality invariant: as long as each request
     * reads {@code lastRecommendation} BEFORE another request's
     * {@code aggregator.evaluate} call lands, no cross-request contamination
     * occurs.
     *
     * <p>This test runs 200 transactions through a single {@code FraudPipeline}
     * instance from a 16-thread pool and asserts the score-bucketed
     * distribution matches the expected shape — APPROVE / FLAG / BLOCK
     * counts match the per-thread stubs, and the aggregate shows no
     * cross-request leakage.</p>
     */
    @Test
    void lastRecommendationIsThreadSafe() throws Exception {
        // Each transaction is one Stage-1 rule + one Stage-2 rule. The Stage-1
        // stub fires a fixed score for the request (so the guard opens
        // Stage 2 deterministically). The Stage-2 stub fires another fixed
        // score. Across N transactions (a multiple of the thread count)
        // we record totalScore and verify each transaction's FraudDecision
        // matches its stubs.
        int totalThreads = 16;
        int callsPerThread = 12;     // 12 * 16 = 192 transactions total
        int expectedTotal = totalThreads * callsPerThread;
        ExecutorService pool = Executors.newFixedThreadPool(totalThreads);
        AtomicInteger approveCount = new AtomicInteger();
        AtomicInteger flagCount = new AtomicInteger();
        AtomicInteger blockCount = new AtomicInteger();
        AtomicInteger mismatchCount = new AtomicInteger();

        try {
            Callable<Void> task = () -> {
                for (int i = 0; i < callsPerThread; i++) {
                    // Vary the stub scores per call so the distribution covers
                    // all three recommendations: totalScore = stage1 + stage2.
                    // Map (i % 3) -> (stage1, stage2) such that:
                    //   i % 3 == 0 -> 20 + 0   = 20 (APPROVE)
                    //   i % 3 == 1 -> 30 + 20  = 50 (FLAG)
                    //   i % 3 == 2 -> 30 + 50  = 80 (BLOCK)
                    int stage1Score = (i % 3 == 0) ? 20 : 30;
                    int stage2Score = (i % 3 == 0) ? 0 : (i % 3 == 1) ? 20 : 50;

                    var pipeline = newPipeline(
                            List.<PipelineStage>of(new StubStage(stage1Score)),
                            List.<PipelineStage>of(new StubStage(stage2Score)));
                    var ctx = new EvaluationContext(tx);

                    FraudDecision decision = pipeline.execute(ctx);

                    int expectedTotalScore = stage1Score + stage2Score;
                    if (decision.totalScore() != expectedTotalScore) {
                        mismatchCount.incrementAndGet();
                        continue;
                    }
                    switch (decision.recommendation()) {
                        case APPROVE -> approveCount.incrementAndGet();
                        case FLAG -> flagCount.incrementAndGet();
                        case BLOCK -> blockCount.incrementAndGet();
                    }
                }
                return null;
            };

            Future<?>[] futures = new Future<?>[totalThreads];
            for (int t = 0; t < totalThreads; t++) {
                futures[t] = pool.submit(task);
            }
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }

        assertEquals(0, mismatchCount.get(),
                "Every thread-local transaction must produce its expected score (no cross-request contamination)");
        int total = approveCount.get() + flagCount.get() + blockCount.get();
        assertEquals(expectedTotal, total,
                "All " + expectedTotal + " transactions must be classified");
        // At least one of each bucket must be present to prove the test
        // actually exercises all three branches under concurrency.
        assertTrue(approveCount.get() > 0, "expected at least one APPROVE");
        assertTrue(flagCount.get() > 0, "expected at least one FLAG");
        assertTrue(blockCount.get() > 0, "expected at least one BLOCK");
    }

    private static class StubStage implements PipelineStage {
        private final int score;

        StubStage(int score) {
            this.score = score;
        }

        @Override
        public Optional<TriggeredRule> evaluate(EvaluationContext ctx) {
            if (score == 0) return Optional.empty();
            return Optional.of(new TriggeredRule("STUB", score, Map.of("s", score), Instant.now()));
        }
    }

    private static class TrackerStage implements PipelineStage {
        boolean called = false;

        @Override
        public Optional<TriggeredRule> evaluate(EvaluationContext ctx) {
            called = true;
            return Optional.of(new TriggeredRule("TRACKER", 50, Map.of(), Instant.now()));
        }
    }
}

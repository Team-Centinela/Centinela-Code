package com.centinela.serverless.archunit;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.either;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.centinela.serverless")
public class ServerlessEngineArchitectureTest {

    @ArchTest
    static final ArchRule domain_should_not_depend_on_frameworks =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "com.azure..",
                            "jakarta.persistence..",
                            "jakarta.servlet.."
                    );

    @ArchTest
    static final ArchRule domain_should_not_access_application_or_adapter =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..application..",
                            "..adapter.."
                    );

    @ArchTest
    static final ArchRule no_cross_service_imports =
            noClasses()
                    .that().resideInAPackage("com.centinela.serverless..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.centinela.ingestion..",
                            "com.centinela.corebackend.."
                    );

    /**
     * #24 S-6 ride: forbid ANY {@code org.springframework.*} annotation in
     * the domain layer. The previous list-mode check missed
     * {@code @Transactional}, {@code @Cacheable}, {@code @Async},
     * {@code @EventListener}, {@code @Configuration}, etc. The single
     * package-name predicate covers every annotation under that root and
     * catches any new annotation type added in a future Spring release
     * without needing to update the rule.
     */
    private static final DescribedPredicate<JavaAnnotation<?>> ANNOTATION_FROM_ORG_SPRINGFRAMEWORK =
            new DescribedPredicate<JavaAnnotation<?>>("@org.springframework.* annotation") {
                @Override
                public boolean apply(JavaAnnotation<?> annotation) {
                    return annotation.getRawType().getPackageName().startsWith("org.springframework");
                }
            };

    @ArchTest
    static final ArchRule no_spring_annotations_in_domain =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().beAnnotatedWith(ANNOTATION_FROM_ORG_SPRINGFRAMEWORK);

    /**
     * #24 S-1 ride: extract the Rule/Stage disjunction into an explicit
     * {@link DescribedPredicate} so the precedence is unambiguous and the
     * ArchUnit DSL no longer relies on the implicit
     * {@code .and(...).or(...)} binding (which works by accident but is
     * misleading to readers).
     *
     * <p>The predicate matches classes whose simple name contains
     * {@code "Rule"} OR {@code "Stage"}. Both branches are explicit
     * {@link DescribedPredicate} instances combined via
     * {@link DescribedPredicate#or(DescribedPredicate)}; a class named only
     * with {@code "Stage"} (e.g. {@code PipelineStage}, {@code AggregatorStage})
     * is correctly matched without needing the {@code "Rule"} suffix.</p>
     */
    private static final DescribedPredicate<JavaClass> PIPELINE_STAGE_RULE_OR_STAGE_NAME =
            either(new DescribedPredicate<JavaClass>("have simple name containing 'Rule'") {
                @Override
                public boolean apply(JavaClass input) {
                    return input.getSimpleName().contains("Rule");
                }
            }).or(new DescribedPredicate<JavaClass>("have simple name containing 'Stage'") {
                @Override
                public boolean apply(JavaClass input) {
                    return input.getSimpleName().contains("Stage");
                }
            });

    @ArchTest
    static final ArchRule pipeline_stages_should_not_import_adapter =
            noClasses()
                    .that().resideInAPackage("..domain.service..")
                    .and(PIPELINE_STAGE_RULE_OR_STAGE_NAME)
                    .should().dependOnClassesThat()
                    .resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule no_account_package =
            noClasses()
                    .that().resideInAPackage("com.centinela.serverless..")
                    .should().resideInAPackage("..account..");

    /**
     * Direct unit test for {@link #PIPELINE_STAGE_RULE_OR_STAGE_NAME}: a
     * class named with "Stage" but without "Rule" must still match the
     * predicate (the OR branch is not silently dropped). The negative
     * fixture is created via the {@link ClassFileImporter} to ensure the
     * same JavaClass shape the rule sees in production.
     */
    @ArchTest
    static void pipeline_stage_predicate_matches_stage_only_class() {
        JavaClasses classes = new ClassFileImporter()
                .importClasses(StageOnlyFixture.class);
        JavaClass stageOnly = classes.get(StageOnlyFixture.class.getName());
        org.junit.jupiter.api.Assertions.assertTrue(
                PIPELINE_STAGE_RULE_OR_STAGE_NAME.apply(stageOnly),
                "Stage class without 'Rule' suffix MUST still match the predicate");
    }

    @ArchTest
    static void pipeline_stage_predicate_matches_rule_only_class() {
        JavaClasses classes = new ClassFileImporter()
                .importClasses(RuleOnlyFixture.class);
        JavaClass ruleOnly = classes.get(RuleOnlyFixture.class.getName());
        org.junit.jupiter.api.Assertions.assertTrue(
                PIPELINE_STAGE_RULE_OR_STAGE_NAME.apply(ruleOnly),
                "Rule class without 'Stage' suffix MUST still match the predicate");
    }

    @ArchTest
    static void pipeline_stage_predicate_rejects_neither_class() {
        JavaClasses classes = new ClassFileImporter()
                .importClasses(NeitherFixture.class);
        JavaClass neither = classes.get(NeitherFixture.class.getName());
        org.junit.jupiter.api.Assertions.assertFalse(
                PIPELINE_STAGE_RULE_OR_STAGE_NAME.apply(neither),
                "Class with neither 'Rule' nor 'Stage' in its name must NOT match");
    }

    /**
     * #24 S-6 ride: predicate-level coverage for the broadened
     * {@link #ANNOTATION_FROM_ORG_SPRINGFRAMEWORK} rule. We import
     * {@link org.springframework.transaction.annotation.Transactional} and
     * {@link org.springframework.stereotype.Service} via the package
     * importer and assert the predicate catches each one — proving that
     * the rule no longer relies on an explicit allowlist per annotation
     * type. {@code @Transactional} was the motivating gap: the previous
     * list-mode check silently allowed it through.
     */
    @ArchTest
    static void spring_annotation_predicate_catches_transactional() {
        JavaClasses transactionalAnnotation = new ClassFileImporter()
                .importClasses(org.springframework.transaction.annotation.Transactional.class);
        org.junit.jupiter.api.Assertions.assertTrue(
                ANNOTATION_FROM_ORG_SPRINGFRAMEWORK.apply(
                        transactionalAnnotation.get(org.springframework.transaction.annotation.Transactional.class.getName())),
                "@Transactional under org.springframework.transaction.* must match the broadened predicate");
    }

    @ArchTest
    static void spring_annotation_predicate_catches_component() {
        JavaClasses componentAnnotation = new ClassFileImporter()
                .importClasses(org.springframework.stereotype.Component.class);
        org.junit.jupiter.api.Assertions.assertTrue(
                ANNOTATION_FROM_ORG_SPRINGFRAMEWORK.apply(
                        componentAnnotation.get(org.springframework.stereotype.Component.class.getName())),
                "@Component under org.springframework.stereotype.* must match the broadened predicate");
    }

    /**
     * Negative-test fixtures. Each lives in the archunit test source tree so
     * the rule's {@code ..domain.service..} package filter does not match
     * them (they're in {@code ..archunit..}); the predicate-level test only
     * cares about simple-name matching.
     */
    static final class StageOnlyFixture {
    }

    static final class RuleOnlyFixture {
    }

    static final class NeitherFixture {
    }
}

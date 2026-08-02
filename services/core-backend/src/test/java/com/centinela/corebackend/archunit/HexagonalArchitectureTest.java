package com.centinela.corebackend.archunit;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class HexagonalArchitectureTest {

    private static final DescribedPredicate<JavaClass> NOT_THIS_TEST = new DescribedPredicate<JavaClass>(
            "not the HexagonalArchitectureTest class itself") {
        @Override
        public boolean test(JavaClass input) {
            return !"HexagonalArchitectureTest".equals(input.getSimpleName());
        }
    };

    private final JavaClasses classes = new ClassFileImporter()
            .importPackages("com.centinela.corebackend", "com.centinela.cases")
            .that(NOT_THIS_TEST);

    @Test
    void domainLayerShouldNotDependOnSpring() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..")
                .because("Domain layer must be pure Java, free of framework annotations")
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void infrastructureLayerShouldNotBeAccessedByDomain() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .because("Domain must not depend on infrastructure adapters")
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void noAccountOrTransactionPackageShouldExist() {
        ArchRule rule = noClasses()
                .should().resideInAPackage("..account..")
                .orShould().resideInAPackage("..transaction..")
                .because("account/ and transaction/ do not belong in Core Backend per ADR-002 (decision #61)");
        rule.check(classes);
    }

    @Test
    void casesConsumerRoutesThroughApplicationUseCase() {
        ArchRule rule = noClasses()
                .that().haveSimpleName("CaseEventsConsumer")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.centinela.cases.adapter.out.persistence.JpaCaseRepository")
                .orShould().dependOnClassesThat()
                .haveFullyQualifiedName("com.centinela.cases.adapter.out.persistence.SpringDataCaseRepository")
                .orShould().dependOnClassesThat()
                .haveFullyQualifiedName("com.centinela.cases.adapter.out.persistence.CaseEntity")
                .because("ADR-001 hexagonal purity: the case-events consumer routes through the application use case, never through the JPA adapter or entity");
        rule.check(classes);
    }

    @Test
    void caseDomainDependsOnlyOnJavaAndJsr305() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.centinela.cases.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "com.centinela.cases.adapter..",
                        "com.centinela.corebackend..")
                .because("ADR-001 §Hexagonal: domain layer is pure Java; adapters are the only place framework imports live");
        rule.check(classes);
    }

    @Test
    void layeredArchitectureShouldBeRespected() {
        ArchRule rule = layeredArchitecture()
                .consideringAllDependencies()
                .layer("domain").definedBy("..domain..")
                .layer("application").definedBy("..application..")
                .layer("infrastructure").definedBy("..infrastructure..", "..adapter..")
                .whereLayer("domain").mayOnlyBeAccessedByLayers("application", "infrastructure")
                .whereLayer("application").mayOnlyBeAccessedByLayers("infrastructure")
                .whereLayer("infrastructure").mayNotBeAccessedByAnyLayer()
                .because("Hexagonal architecture: domain is innermost, application orchestrates, infrastructure adapts");

        rule.allowEmptyShould(true).check(classes);
    }
}

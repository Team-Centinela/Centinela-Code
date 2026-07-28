package com.centinela.serverless.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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

    @ArchTest
    static final ArchRule no_spring_annotations_in_domain =
            classes()
                    .that().resideInAPackage("..domain..")
                    .should().notBeAnnotatedWith(org.springframework.stereotype.Service.class)
                    .andShould().notBeAnnotatedWith(org.springframework.stereotype.Component.class)
                    .andShould().notBeAnnotatedWith(org.springframework.stereotype.Repository.class)
                    .andShould().notBeAnnotatedWith(org.springframework.boot.autoconfigure.SpringBootApplication.class);

    @ArchTest
    static final ArchRule pipeline_stages_should_not_import_adapter =
            noClasses()
                    .that().resideInAPackage("..domain.service..")
                    .and().haveSimpleNameContaining("Rule")
                    .or().haveSimpleNameContaining("Stage")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule no_account_package =
            noClasses()
                    .that().resideInAPackage("com.centinela.serverless..")
                    .should().resideInAPackage("..account..");
}

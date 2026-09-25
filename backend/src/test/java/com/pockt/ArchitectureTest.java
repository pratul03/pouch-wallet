package com.pockt;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.pockt", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    @ArchTest
    static final ArchRule no_module_cross_internal_imports =
            noClasses()
                    .that().resideInAPackage("com.pockt.transfer..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.pockt.wallet.internal..");

    @ArchTest
    static final ArchRule no_transfer_internal_imports_in_notification =
            noClasses()
                    .that().resideInAPackage("com.pockt.notification..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.pockt.transfer.internal..");

    @ArchTest
    static final ArchRule repositories_only_in_repository_packages =
            classes()
                    .that().haveSimpleNameEndingWith("Repository")
                    .and().areNotInterfaces()
                    .and().doNotBelongToAnyOf(com.pockt.infrastructure.persistence.BaseRepository.class)
                    .should().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule no_jdbc_in_service_layer =
            noClasses()
                    .that().resideInAPackage("..service..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");
}

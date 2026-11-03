package com.sinai.app;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "com.sinai", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule storageIsUsedOnlyThroughApiAndConfig = onlyPublicPackagesUsedFromOutside("storage");

    @ArchTest
    static final ArchRule mediaIsUsedOnlyThroughApiAndConfig = onlyPublicPackagesUsedFromOutside("media");

    @ArchTest
    static final ArchRule aiIsUsedOnlyThroughApiAndConfig = onlyPublicPackagesUsedFromOutside("ai");

    @ArchTest
    static final ArchRule identityIsUsedOnlyThroughApi = noClasses()
            .that().resideOutsideOfPackage("com.sinai.identity..")
            .should().dependOnClassesThat(resideInAPackage("com.sinai.identity..")
                    .and(not(resideInAPackage("com.sinai.identity.api.."))))
            .because("other modules must use identity only through its api package");

    @ArchTest
    static final ArchRule modulesDoNotDependOnApp = noClasses()
            .that().resideOutsideOfPackage("com.sinai.app..")
            .should().dependOnClassesThat().resideInAPackage("com.sinai.app..");

    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .because("controllers go through services");

    @ArchTest
    static final ArchRule servicesDoNotUseControllers = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule repositoriesDoNotUseUpperLayers = noClasses()
            .that().resideInAPackage("..repository..")
            .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..");

    @ArchTest
    static final ArchRule entitiesAreNotExposedByTheWebLayer = noClasses()
            .that().resideInAnyPackage("..controller..", "..model.request..", "..model.response..")
            .should().dependOnClassesThat().resideInAPackage("..model.entity..")
            .because("the API exposes request/response records, never JPA entities");

    @ArchTest
    static final ArchRule restControllersLiveInControllerPackages = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..controller..")
            .andShould().haveSimpleNameEndingWith("Controller");

    @ArchTest
    static final ArchRule noFieldInjection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static void everyModuleIsImported(JavaClasses classes) {
        var packages = classes.stream().map(JavaClass::getPackageName).toList();
        assertThat(packages).anyMatch(name -> name.startsWith("com.sinai.common"))
                .anyMatch(name -> name.startsWith("com.sinai.storage"))
                .anyMatch(name -> name.startsWith("com.sinai.media"))
                .anyMatch(name -> name.startsWith("com.sinai.ai"))
                .anyMatch(name -> name.startsWith("com.sinai.identity"))
                .anyMatch(name -> name.startsWith("com.sinai.workflow"))
                .anyMatch(name -> name.startsWith("com.sinai.app"));
        assertThat(classes.contain("com.sinai.identity.controller.AuthController")).isTrue();
    }

    private static ArchRule onlyPublicPackagesUsedFromOutside(String module) {
        var root = "com.sinai." + module;
        return noClasses()
                .that().resideOutsideOfPackage(root + "..")
                .should().dependOnClassesThat(resideInAPackage(root + "..")
                        .and(not(resideInAnyPackage(root + ".api..", root + ".config.."))))
                .because(root + ".internal is private to its module");
    }
}

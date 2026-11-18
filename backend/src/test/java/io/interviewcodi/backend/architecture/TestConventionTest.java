package io.interviewcodi.backend.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

/**
 * 테스트 코드의 컨벤션. 테스트 메서드는 동작을 한국어 {@code @DisplayName}으로 설명한다.
 */
@AnalyzeClasses(packages = "io.interviewcodi.backend", importOptions = ImportOption.OnlyIncludeTests.class)
class TestConventionTest {

    @ArchTest
    static final ArchRule TESTS_HAVE_DISPLAY_NAME = methods()
            .that().areAnnotatedWith(Test.class)
            .or().areAnnotatedWith(ParameterizedTest.class)
            .should().beAnnotatedWith(DisplayName.class);
}

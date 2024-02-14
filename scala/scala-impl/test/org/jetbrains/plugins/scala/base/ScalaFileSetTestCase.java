package org.jetbrains.plugins.scala.base;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ThrowableRunnable;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.FileSetTests;
import org.jetbrains.plugins.scala.ScalaLanguage;
import org.jetbrains.plugins.scala.ScalaVersion;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.junit.experimental.categories.Category;
import scala.collection.immutable.Seq;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

import static com.intellij.openapi.util.io.FileUtil.loadFileText;
import static com.intellij.openapi.util.text.StringUtil.*;
import static org.junit.Assert.*;

public abstract class ScalaFileSetTestCase extends TestSuite {

    protected ScalaFileSetTestCase(@NotNull @NonNls String path, String... testFileExtensions) {
        String pathProperty = System.getProperty("path");
        String customOrPropertyPath = pathProperty != null
                ? pathProperty
                : getTestDataPath() + path;

        File directory = new File(customOrPropertyPath);
        Stream<File> testFiles = FileUtils.listFiles(directory, null, true)
                .stream()
                .filter(file -> isTestFile(file, testFileExtensions));

        Stream<Test> testCases = testFiles.map(this::constructTestCase);
        testCases.forEach(this::addTest);

        assertTrue("No tests found", testCount() > 0);
    }

    protected boolean needsSdk() {
        return false;
    }

    private Test constructTestCase(File file) {
        if (needsSdk())
            return new ActualTest(file);
        return new NoSdkTestCase(file);
    }

    protected void setUp(@NotNull Project project) {
        setSettings(project);
    }

    protected void tearDown(@NotNull Project project) {
    }

    @NotNull
    protected String getTestDataPath() {
        return TestUtils.getTestDataPath();
    }

    @NotNull
    protected Language getLanguage() {
        return ScalaLanguage.INSTANCE;
    }

    //used just to propagate to ActualTest.supportedIn
    //default implementation took from org.jetbrains.plugins.scala.base.ScalaSdkOwner.supportedIn
    //TODO: consider using Scala 2.13 by default
    protected boolean supportedInScalaVersion(ScalaVersion version) {
        return true;
    }

    @NotNull
    @Override
    public final String getName() {
        return getClass().getName();
    }

    protected void setSettings(@NotNull Project project) {
        CommonCodeStyleSettings.IndentOptions indentOptions = getCommonSettings(project).getIndentOptions();
        assertNotNull(indentOptions);
        setIndentSettings(indentOptions);
    }

    protected void setIndentSettings(@NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
        indentOptions.INDENT_SIZE = 2;
        indentOptions.CONTINUATION_INDENT_SIZE = 2;
        indentOptions.TAB_SIZE = 2;
    }

    @NotNull
    abstract protected String transform(
            @NotNull String testName,
            @NotNull String fileText,
            @NotNull Project project
    );

    @NotNull
    protected String transformExpectedResult(@NotNull String text) {
        return text;
    }

    protected void runTest(@NotNull final String testName0,
                           @NotNull final String content0,
                           @NotNull final Project project) {
        Seq<String> input = TestUtils.readInputFromFileText(content0);

        //NOTE: if a test file doesn't have a separator, there is a single element in `input`.
        //In this case, it's expected that the code is not changed in the end
        final String before = input.head();
        final String after0 = input.last();
        final String after = transformExpectedResult(after0.trim());

        assertTrue("No data found in source file", input.nonEmpty());

        final int dotIdx = testName0.indexOf('.');
        final String testName = dotIdx >= 0 ? testName0.substring(0, dotIdx) : testName0;

        //TODO: don't do the trim by default in all tests
        final String afterActual = transform(testName, before, project).trim();
        if (shouldPass()) {
            assertEquals(after, afterActual);
        } else {
            assertNotEquals(after, afterActual);
        }
    }

    protected boolean shouldPass() {
        return true;
    }

    @SuppressWarnings({"UnconstructableJUnitTestCase", "JUnitMalformedDeclaration"})
    @Category({FileSetTests.class})
    private final class NoSdkTestCase extends LightJavaCodeInsightFixtureTestCase {
        private final File testFile;

        private NoSdkTestCase(@NotNull File testFile) {
            this.testFile = testFile;
        }

        @Override
        protected void setUp() throws Exception {
            TestUtils.optimizeSearchingForIndexableFiles();
            super.setUp();
            ScalaFileSetTestCase.this.setUp(getProject());
        }

        @Override
        protected void tearDown() throws Exception {
            try {
                ScalaFileSetTestCase.this.tearDown(getProject());
            } finally {
                super.tearDown();
            }
        }

        @Override
        public void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
            final var fileText = FileUtil.loadFile(testFile, StandardCharsets.UTF_8);
            try {
                ScalaFileSetTestCase.this.runTest(
                        testFile.getName(),
                        convertLineSeparators(fileText),
                        getProject()
                );
            } catch(Throwable error) {
                // to be able to Ctrl + Click in console to nabigate to test file on failure
                // (note, can not work with Android plugin disabled, see IDEA-257969)
                System.err.println("### Test file: " + testFile.getAbsolutePath());
                throw error;
            }
        }

        @NotNull
        @Override
        public String toString() {
            return getName();
        }

        @NotNull
        @Override
        public String getName() {
            final var name = testFile.getName();
            final var dotIndex = name.lastIndexOf('.');
            if (dotIndex == -1) {
                return name;
            }
            return name.substring(0, dotIndex);
        }
    }

    @SuppressWarnings({"UnconstructableJUnitTestCase", "JUnitMalformedDeclaration"})
    @Category({FileSetTests.class})
    private final class ActualTest extends ScalaLightCodeInsightFixtureTestCase {

        private final File myTestFile;

        private ActualTest(@NotNull File testFile) {
            myTestFile = testFile;
        }

        @Override
        public boolean supportedIn(ScalaVersion version) {
            return supportedInScalaVersion(version);
        }

        @Override
        public void setUp() {
            try {
                super.setUp();
                ScalaFileSetTestCase.this.setUp(getProject());
                TestUtils.disableTimerThread();
            } catch (Exception e) {
                try {
                    tearDown();
                } catch (Exception ignored) {
                }
                throw e;
            }
        }

        @Override
        public void tearDown() {
            ScalaFileSetTestCase.this.tearDown(getProject());
            try {
                super.tearDown();
            } catch (IllegalArgumentException ignored) {
            }
        }

        @Override
        public void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
            String fileText = new String(loadFileText(myTestFile, "UTF-8"));
            try {
                ScalaFileSetTestCase.this.runTest(
                        myTestFile.getName(),
                        convertLineSeparators(fileText),
                        getProject()
                );
            } catch(Throwable error) {
                // to be able to Ctrl + Click in console to navigate to test file on failure
                // (note, can not work with Android plugin disabled, see IDEA-257969)
                System.err.println("### Test file: " + myTestFile.getAbsolutePath());
                throw error;
            }
        }

        @NotNull
        @Override
        protected String getTestName(boolean lowercaseFirstLetter) {
            return "";
        }

        @NotNull
        @Override
        public String toString() {
            return getName() + " ";
        }

        @NotNull
        @Override
        public String getName() {
            return myTestFile.getAbsolutePath();
        }
    }

    @NotNull
    protected final ScalaCodeStyleSettings getScalaSettings(@NotNull Project project) {
        return CodeStyle.getSettings(project).getCustomSettings(ScalaCodeStyleSettings.class);
    }

    @NotNull
    protected final CommonCodeStyleSettings getCommonSettings(@NotNull Project project) {
        return CodeStyle.getSettings(project).getCommonSettings(ScalaLanguage.INSTANCE);
    }

    protected final PsiFile createLightFile(@NotNull @NonNls String text,
                                            @NotNull Project project) {
        return PsiFileFactory.getInstance(project).createFileFromText(
                "dummy.scala",
                getLanguage(),
                text
        );
    }

    private static boolean isTestFile(@NotNull File file, String[] testFileExtensions) {
        String path = file.getAbsolutePath();
        String name = file.getName();

        if (testFileExtensions.length == 0) {
            testFileExtensions = new String[] { ".test" };
        }

        return Arrays.stream(testFileExtensions).anyMatch(ext -> endsWith(name, ext)) &&
                !path.contains(".svn") &&
                !path.contains(".cvs") &&
                !startsWithChar(name, '_') &&
                !"CVS".equals(name);
    }
}

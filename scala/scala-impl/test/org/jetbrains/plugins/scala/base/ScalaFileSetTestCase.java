/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.base;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.ThrowableRunnable;
import junit.framework.TestSuite;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaLanguage;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;
import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.intellij.openapi.util.io.FileUtil.loadFileText;
import static com.intellij.openapi.util.text.StringUtil.*;
import static com.intellij.psi.impl.DebugUtil.psiToString;
import static org.junit.Assert.*;

/**
 * Author: Ilya Sergey
 * Date: 01.11.2006
 * Time: 15:51:24
 */
public abstract class ScalaFileSetTestCase extends TestSuite {

    protected ScalaFileSetTestCase(@NotNull @NonNls String path) {
        super();

        String pathProperty = System.getProperty("path");
        String customOrPropertyPath = pathProperty != null ?
                pathProperty :
                getTestDataPath() + path;

        findFiles(new File(customOrPropertyPath))
                .filter(ScalaFileSetTestCase::isTestFile)
                .map(ActualTest::new)
                .forEach(this::addTest);

        assertTrue("No tests found", testCount() > 0);
    }

    protected SharedTestProjectToken sharedTestProjectToken() {
        return SharedTestProjectToken.apply(this);
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

    // TODO: make this method abstract and reuse implementation using e.g. mixins in parser tests
    //  this method builds psi tree string and it's only applicable to parser tests
    @NotNull
    protected String transform(@NotNull String testName,
                               @NotNull String fileText,
                               @NotNull Project project) {
        PsiFile lightFile = createLightFile(fileText, project);

        return psiToString(lightFile, false)
                .replace(": " + lightFile.getName(), "");
    }

    protected void runTest(@NotNull final String testName0,
                           @NotNull final String content0,
                           @NotNull final Project project) {
        final List<String> input = new ArrayList<>();

        int separatorIndex;
        // Adding input  before -----
        String content = content0;
        while ((separatorIndex = content.indexOf("-----")) >= 0) {
            input.add(content.substring(0, separatorIndex - 1));
            content = content.substring(separatorIndex);
            while (startsWithChar(content, '-') ||
                    startsWithChar(content, '\n')) {
                content = content.substring(1);
            }
        }

        // Result - after -----
        String result = content;
        while (startsWithChar(result, '-') ||
                startsWithChar(result, '\n') ||
                startsWithChar(result, '\r')) {
            result = result.substring(1);
        }

        if (result.trim().toLowerCase().equals("UNCHANGED_TAG")) {
            assertEquals("Unchenged expected result expects only 1 input enty", 1, input.size());
            result = input.get(0);
        }

        assertFalse("No data found in source file", input.isEmpty());
        assertNotNull(result);

        final String testName;
        final int dotIdx = testName0.indexOf('.');
        testName = dotIdx >= 0 ? testName0.substring(0, dotIdx) : testName0;

        String temp = transform(testName, input.get(0), project);
        result = result.trim();

        final String transformed = convertLineSeparators(temp).trim();

        if (shouldPass()) {
            assertEquals(result, transformed);
        } else {
            assertNotEquals(result, transformed);
        }
    }

    protected boolean shouldPass() {
        return true;
    }

    @SuppressWarnings("UnconstructableJUnitTestCase")
    private final class ActualTest extends ScalaLightCodeInsightFixtureTestAdapter {

        private final File myTestFile;

        private ActualTest(@NotNull File testFile) {
            myTestFile = testFile;
        }

        @Override
        public SharedTestProjectToken sharedProjectToken() {
            return ScalaFileSetTestCase.this.sharedTestProjectToken();
        }

        @Override
        protected void setUp() throws Exception {
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
        protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
            String fileText = new String(loadFileText(myTestFile, "UTF-8"));
            try {
                ScalaFileSetTestCase.this.runTest(
                        myTestFile.getName(),
                        convertLineSeparators(fileText),
                        getProject()
                );
            } catch(Throwable error) {
                // to be able to Ctrl + Click in console to nabigate to test file on failure
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
        return getSettings(project).getCustomSettings(ScalaCodeStyleSettings.class);
    }

    @NotNull
    protected final CommonCodeStyleSettings getCommonSettings(@NotNull Project project) {
        return getSettings(project).getCommonSettings(ScalaLanguage.INSTANCE);
    }

    protected final PsiFile createLightFile(@NotNull @NonNls String text,
                                            @NotNull Project project) {
        return PsiFileFactory.getInstance(project).createFileFromText(
                "dummy.scala",
                getLanguage(),
                text
        );
    }

    @NotNull
    private static CodeStyleSettings getSettings(@NotNull Project project) {
        return CodeStyle.getSettings(project);
    }

    @NotNull
    private static Stream<File> findFiles(@NotNull File baseFile) {
        if (baseFile.exists()) {
            List<File> myFiles = new ArrayList<>();
            scanForFiles(baseFile, myFiles);
            return myFiles.stream();
        } else {
            return Stream.empty();
        }
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    private static void scanForFiles(@NotNull File directory,
                                     @NotNull List<File> accumulator) {
        // recursively scan for all subdirectories
        if (directory.isDirectory()) {
            for (File file : Objects.requireNonNull(directory.listFiles())) {
                if (file.isDirectory()) {
                    scanForFiles(file, accumulator);
                } else {
                    accumulator.add(file);
                }
            }
        }
    }

    private static boolean isTestFile(@NotNull File file) {
        String path = file.getAbsolutePath();
        String name = file.getName();

        return !path.contains(".svn") &&
                !path.contains(".cvs") &&
                endsWith(name, ".test") &&
                !startsWithChar(name, '_') &&
                !"CVS".equals(name);
    }
}

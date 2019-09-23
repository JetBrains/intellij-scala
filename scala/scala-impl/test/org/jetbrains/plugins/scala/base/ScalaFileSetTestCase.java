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
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import junit.framework.TestSuite;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaLanguage;
import org.jetbrains.plugins.scala.ScalaLoader;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.intellij.openapi.util.io.FileUtil.loadFileText;
import static com.intellij.openapi.util.text.StringUtil.*;
import static org.jetbrains.plugins.scala.util.TestUtils.disableTimerThread;
import static org.jetbrains.plugins.scala.util.TestUtils.getTestDataPath;
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
    }

    protected void setUp(@NotNull Project project) {
        ScalaLoader.loadScala();
        setSettings(project);
    }

    protected void tearDown(@NotNull Project project) {
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
    protected abstract String transform(@NotNull String testName,
                                        @NotNull String fileText,
                                        @NotNull Project project) throws IOException;

    protected void runTest(@NotNull String testName,
                           @NotNull String content,
                           @NotNull Project project) throws IOException {
        final List<String> input = new ArrayList<>();

        int separatorIndex;
        // Adding input  before -----
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

        final int dotIdx = testName.indexOf('.');
        if (dotIdx >= 0) {
            testName = testName.substring(0, dotIdx);
        }

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
        protected void setUp() throws Exception {
            try {
                super.setUp();
                ScalaFileSetTestCase.this.setUp(getProject());
                disableTimerThread();
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
        protected void runTest() throws Throwable {
            String fileText = new String(loadFileText(myTestFile, "UTF-8"));
            ScalaFileSetTestCase.this.runTest(
                    myTestFile.getName(),
                    convertLineSeparators(fileText),
                    getProject()
            );
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

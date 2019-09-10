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

package org.jetbrains.plugins.scala.testcases;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaLanguage;
import org.jetbrains.plugins.scala.ScalaLoader;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.convertLineSeparators;
import static com.intellij.openapi.util.text.StringUtil.startsWithChar;
import static org.junit.Assert.*;

/**
 * Author: Ilya Sergey
 * Date: 01.11.2006
 * Time: 15:51:24
 */
public abstract class BaseScalaFileSetTestCase extends FileSetTestCase {

    protected BaseScalaFileSetTestCase(String path) {
        super(path);
    }

    @Override
    public void setUp(Project project) {
        super.setUp(project);
        ScalaLoader.loadScala();
        setSettings();
    }

    @NotNull
    private CodeStyleSettings getSettings() {
        return CodeStyle.getSettings(getProject());
    }

    @NotNull
    protected final ScalaCodeStyleSettings getScalaSettings() {
        return getSettings().getCustomSettings(ScalaCodeStyleSettings.class);
    }

    @NotNull
    protected final CommonCodeStyleSettings getCommonSettings() {
        return getSettings().getCommonSettings(ScalaLanguage.INSTANCE);
    }

    protected void setSettings() {
        CommonCodeStyleSettings.IndentOptions indentOptions = getCommonSettings().getIndentOptions();
        assertNotNull(indentOptions);

        indentOptions.INDENT_SIZE = 2;
        indentOptions.CONTINUATION_INDENT_SIZE = 2;
        indentOptions.TAB_SIZE = 2;
    }

    @NotNull
    protected abstract String transform(@NotNull String testName,
                                        @NotNull String fileText,
                                        @NotNull Project project) throws IOException;

    public void runTest(@NotNull File testFile,
                        @NotNull Project project) throws IOException {
        String content = convertLineSeparators(new String(FileUtil.loadFileText(testFile, "UTF-8")));

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

        String testName = testFile.getName();
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
}

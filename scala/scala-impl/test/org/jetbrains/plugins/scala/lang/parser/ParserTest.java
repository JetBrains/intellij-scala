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

package org.jetbrains.plugins.scala.lang.parser;

import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import junit.framework.Test;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

import static org.jetbrains.plugins.scala.util.TestUtils.createPseudoPhysicalScalaFile;
import static org.jetbrains.plugins.scala.util.TestUtils.getTestDataPath;

@RunWith(AllTests.class)
public class ParserTest extends BaseScalaFileSetTestCase {

    public ParserTest() {
        super(findPath());
    }

    public String transform(String testName, String[] data) {
        PsiFile psiFile = createPseudoPhysicalScalaFile(getProject(), data[0]);
        return DebugUtil.psiToString(psiFile, false)
                .replace(": " + psiFile.getName(), "");
    }

    public static Test suite() {
        return new ParserTest();
    }

    private static String findPath() {
        String path = System.getProperty("path");
        return path == null ? getTestDataPath() + "/parser/data" : path;
    }
}



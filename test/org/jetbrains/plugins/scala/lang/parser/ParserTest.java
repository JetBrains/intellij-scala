/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.lang.parser;

import org.jetbrains.plugins.scala.Console;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;
import org.jetbrains.plugins.scala.util.TestUtils;

public class ParserTest extends BaseScalaFileSetTestCase {
  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/scala/lang/parser/data";

  public ParserTest() {
    super(System.getProperty("path") != null ?         
            System.getProperty("path") :
            DATA_PATH                          
    );
  }


  public String transform(String testName, String[] data) throws Exception {
    String fileText = data[0];
    PsiFile psiFile = TestUtils.createPseudoPhysicalScalaFile(getProject(), fileText);

    String psiTree = DebugUtil.psiToString(psiFile, false).replace(":" + psiFile.getName(), "");
    Console.println("------------------------ " + testName + " ------------------------");
    Console.println(psiTree);
    Console.println("");

    return psiTree;

  }

  public static Test suite() {
    return new ParserTest();
  }

}



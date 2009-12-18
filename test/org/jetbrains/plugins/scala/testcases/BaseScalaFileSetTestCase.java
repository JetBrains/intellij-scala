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

package org.jetbrains.plugins.scala.testcases;

/**
 * Author: Ilya Sergey
 * Date: 01.11.2006
 * Time: 15:51:24
 */

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import junit.framework.Assert;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseScalaFileSetTestCase extends ScalaFileSetTestCase {
  public BaseScalaFileSetTestCase(String path) {
    super(path);
  }

  public abstract String transform(String testName, String[] data) throws Exception;

  protected void runTest(final File myTestFile) throws Throwable {

    String content = new String(FileUtil.loadFileText(myTestFile, "UTF-8"));
    Assert.assertNotNull(content);

    List<String> input = new ArrayList<String>();

    int separatorIndex;
    content = StringUtil.replace(content, "\r", ""); // for MACs

    // Adding input  before -----
    while ((separatorIndex = content.indexOf("-----")) >= 0) {
      input.add(content.substring(0, separatorIndex-1));
      content = content.substring(separatorIndex);
      while (StringUtil.startsWithChar(content, '-') ||
              StringUtil.startsWithChar(content, '\n')) {
        content = content.substring(1);
      }
    }
    // Result - after -----
    String result = content;
    while (StringUtil.startsWithChar(result, '-') ||
            StringUtil.startsWithChar(result, '\n') ||
            StringUtil.startsWithChar(result, '\r')) {
      result = result.substring(1);
    }

    Assert.assertTrue("No data found in source file", input.size() > 0);
    Assert.assertNotNull(result);
    Assert.assertNotNull(input);


    final String transformed;
    String testName = myTestFile.getName();
    final int dotIdx = testName.indexOf('.');
    if (dotIdx >= 0) {
      testName = testName.substring(0, dotIdx);
    }

    String temp = transform(testName, input.toArray(new String[input.size()]));
    transformed = StringUtil.replace(temp ,"\r", "");

    Assert.assertEquals(result.trim(), transformed.trim());

  }


}

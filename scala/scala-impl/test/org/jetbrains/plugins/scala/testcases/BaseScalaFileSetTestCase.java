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
import org.junit.Assert;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

//import java.nio.file.FileSystems;
//import java.nio.file.Files;
//import java.nio.file.Path;

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

    if (shouldPass()) {
      Assert.assertEquals(result.trim(), transformed.trim());
    } else {
      Assert.assertNotEquals(result.trim(), transformed.trim());
    };

    //fixTestData(..., input, transformed)
  }

  protected boolean shouldPass() {
    return true;
  }

  //creates directory on dataPath1 with new fixed testData
  /*private void fixTestData(String dataPath, String[] input, String result) {
    String newDataPath = dataPath + "1";
    Path relPath = FileSystems.getDefault().getPath(dataPath).relativize(myFile.toPath());
    File newFile = FileSystems.getDefault().getPath(newDataPath).resolve(relPath).toFile();
    File parentDir = newFile.getParentFile();
    try {
      if (!parentDir.exists()) {
        Files.createDirectories(parentDir.toPath());
      }
      if (!newFile.exists()) {
        Files.createFile(newFile.toPath());
      }
      BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(newFile));
      for (String s: input) {
        bos.write(s.getBytes());
      }
      bos.write("\n-----\n".getBytes());
      bos.write(result.getBytes());
      bos.close();
    } catch (IOException e) {}
  }*/


}

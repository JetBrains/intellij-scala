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

public abstract class BaseScalaFileSetTestCase extends ScalaFileSetTestCase {
  public BaseScalaFileSetTestCase(String path) {
    super(path);
  }

  public abstract String transform(String testName, String[] data, String[] outTree) throws Exception;

  protected void runTest(final File myTestFile) throws Throwable {

    String content = new String(FileUtil.loadFileText(myTestFile));
    Assert.assertNotNull(content);

    List<String> input = new ArrayList<String>();
    List<String> output = new ArrayList<String>();

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
      output.add(result);
      result = result.substring(1);
    }

    Assert.assertTrue("No data found in source file", input.size() > 0);
    Assert.assertNotNull(result);
    Assert.assertNotNull(input);

    System.out.println("result: " + result);

    final String transformed;
    String testName = myTestFile.getName();
    final int dotIdx = testName.indexOf('.');
    if (dotIdx >= 0) {
      testName = testName.substring(0, dotIdx);
    }

    String temp = transform(testName, input.toArray(new String[input.size()]),
        output.toArray(new String[output.size()]));
    
    transformed = StringUtil.replace(temp ,"\r", "");
//
//    result = StringUtil.replace(result, "\r", "");
//
    Assert.assertEquals(result.trim(), transformed.trim());

  }


}

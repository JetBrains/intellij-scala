package org.jetbrains.plugins.scala.lang.parser;

import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import junit.framework.Assert;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;

import java.io.File;
import java.util.List;
import java.util.ArrayList;

public class SourceTest extends BaseScalaFileSetTestCase {
  @NonNls
  private static final String DATA_PATH = "C:/Work/src/scala/collection";
  protected static final String TEST_FILE_PATTERN = "(.*)\\.scala";

  public SourceTest() {
    super(DATA_PATH);
  }


  public String transform(String testName, String[] data) throws Exception {
    //throw new UnsupportedOperationException("transform not implemented in org.jetbrains.plugins.scala.lang.parser.SourceTest");
    return "bugaga";
  }

  protected void runTest(final File myTestFile) throws Throwable {

    String content = new String(FileUtil.loadFileText(myTestFile));
    String testName = myTestFile.getName();
    Assert.assertNotNull(content);

    content = StringUtil.replace(content, "\r", ""); // for MACs
    String temp = transform(testName, content);
    String transformed = StringUtil.replace(temp ,"\r", "");

    Assert.assertFalse(testName, transformed.contains("PsiErrorElement"));
  }


  public String transform(String testName, String fileText) throws Exception {

    PsiManager psiManager = PsiManager.getInstance(project);
    PsiElementFactory psiElementFactory = psiManager.getElementFactory();
    Assert.assertNotNull(psiElementFactory);
    Assert.assertNotNull(TEMP_FILE);
    Assert.assertNotNull(fileText);
    PsiFile psiFile = psiElementFactory.createFileFromText(TEMP_FILE, fileText);
    String psiTree = DebugUtil.psiToString(psiFile, false);
    return psiTree;

  }

  public static Test suite() {
    return new SourceTest();
  }

  public String getSearchPattern() {
    return TEST_FILE_PATTERN;
  }

}




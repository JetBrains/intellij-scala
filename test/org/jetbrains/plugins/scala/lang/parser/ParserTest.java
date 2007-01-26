package org.jetbrains.plugins.scala.lang.parser;

import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import junit.framework.Assert;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;
import org.jetbrains.plugins.scala.util.TestUtils;

public class ParserTest extends BaseScalaFileSetTestCase {
  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/scala/lang/parser/data/expressions";
  //private static final String DATA_PATH = "test/org/jetbrains/plugins/scala/lang/parser/data/actual";

  public ParserTest() {
    super(  System.getProperty("path")!=null ?
            System.getProperty("path") :
            DATA_PATH
    );
  }


  public String transform(String testName, String[] data) throws Exception {
    String fileText = data[0];

    PsiFile psiFile = TestUtils.createPseudoPhysicalFile(project, fileText);

    String psiTree = DebugUtil.psiToString(psiFile, false);
    System.out.println("------------------------ "+testName+" ------------------------");
    System.out.println(psiTree);
    System.out.println("");

    return psiTree;

  }

  public static Test suite() {
    return new ParserTest();
  }

}



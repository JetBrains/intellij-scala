package org.jetbrains.plugins.scala.lang.scaladocparser;

import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.Console;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;
import org.jetbrains.plugins.scala.util.TestUtils;

/**
 * User: Dmitry Naidanov
 * Date: 11/16/11
 */
public class ScaladocParserTest extends BaseScalaFileSetTestCase {
  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/scala/lang/parser/scaladocdata";

  public ScaladocParserTest() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH
    );
  }


  public String transform(String testName, String[] data) throws Exception {
    String fileText = data[0];
    PsiFile psiFile = TestUtils.createPseudoPhysicalScalaFile(myProject, fileText);

    String psiTree = DebugUtil.psiToString(psiFile, false).replace(":" + psiFile.getName(), "");
    Console.println("------------------------ " + testName + " ------------------------");
    Console.println(psiTree);
    Console.println("");

    return psiTree;

  }

  public static Test suite() {
    return new ScaladocParserTest();
  }

}

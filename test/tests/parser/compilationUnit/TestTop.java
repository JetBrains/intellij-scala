package tests.parser.compilationUnit;

import junit.framework.TestCase;
import com.intellij.testFramework.ParsingTestCase;

/**
 * User: Dmitry.Krasilschikov
 * Date: 23.10.2006
 * Time: 14:15:48
 */
public class TestTop extends ParsingTestCase {
  public TestTop(String dataPath, String fileExt) {
    super(dataPath, fileExt);
  }

  private static final String classA = "classA.scala";
  void testClass() {
    //ScalaParser parser = ScalaParser()  
  }
}

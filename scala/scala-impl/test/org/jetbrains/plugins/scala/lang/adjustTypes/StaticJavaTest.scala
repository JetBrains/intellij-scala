package org.jetbrains.plugins.scala.lang.adjustTypes

import com.intellij.psi.PsiNamedElement
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.junit.Assert

class StaticJavaTest extends LightJavaCodeInsightFixtureTestCase {

  def testStaticJava(): Unit = {
    val file = myFixture.addFileToProject("TestStatic.java",
      """public class TestStatic {
        |
        |    public static int staticField = 0;
        |
        |    public static int staticMethod() {
        |        return 1;
        |    }
        |
        |    enum Enum {
        |        A1;
        |
        |        public static String enumStaticField = "";
        |        public String enumField = "";
        |
        |        enum Enum2 {
        |            B1;
        |        }
        |    }
        |
        |    interface Interface {
        |        String interfaceField = "";
        |    }
        |
        |    class Inner {
        |        public int innerField = 1;
        |
        |        public static int innerStaticField = 2; //compile error
        |    }
        |
        |    public static class StaticInner {
        |        public int staticClassField = 1;
        |
        |        public static int staticClassStaticField = 2;
        |    }
        |}""".stripMargin.replace("\r", ""))

    val hasStablePaths = file.depthFirst().collect {
      case named: PsiNamedElement if ScalaPsiUtil.hasStablePath(named) => named.name
    }

    Assert.assertEquals(hasStablePaths.toSet,
      Set("TestStatic", "staticField", "staticMethod", "Enum", "A1", "enumStaticField", "Enum2", "B1",
        "Interface", "interfaceField", "StaticInner", "staticClassStaticField"))
  }
}

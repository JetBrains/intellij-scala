package org.jetbrains.plugins.scala
package lang
package completion3

import org.junit.Assert.assertTrue

/**
  * @author Alefas
  * @since 23.03.12
  */
class ScalaLookupRenderingTest extends ScalaCodeInsightTestBase {

  import ScalaCodeInsightTestBase._

  def testJavaVarargs() {
    configureJavaFile(
      fileText =
        """
          |package a;
          |
          |public class Java {
          |  public static void foo(int... x) {}
          |}
        """.stripMargin,
      className = "Java",
      packageName = "a"
    )

    val lookups = configureTest(fileText =
      """
        |import a.Java
        |class A {
        |  Java.fo<caret>
        |}
      """.stripMargin) {
      hasLookupString(_, "foo")
    }

    val result = lookups.map(renderLookupElement)
      .map(presentation => s"${presentation.getItemText}${presentation.getTailText}")
      .headOption

    assertTrue(result.contains("foo(x: Int*)"))
  }
}

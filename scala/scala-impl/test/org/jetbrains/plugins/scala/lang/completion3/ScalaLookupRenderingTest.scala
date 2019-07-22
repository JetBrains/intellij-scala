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

  def testJavaVarargs(): Unit = {
    this.configureJavaFile(
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

    configureTest(
      fileText =
        """
          |import a.Java
          |class A {
          |  Java.fo<caret>
          |}
        """.stripMargin
    )

    val condition = lookupItems.exists {
      hasItemText(_, "foo")(itemTextBold = true, tailText = "(x: Int*)")
    }
    assertTrue(condition)
  }
}

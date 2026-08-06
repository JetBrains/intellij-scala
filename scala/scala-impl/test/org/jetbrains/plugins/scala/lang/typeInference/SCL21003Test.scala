package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.TypecheckerTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class SCL21003Test extends ScalaLightCodeInsightFixtureTestCase {
  def testSCL21003(): Unit = {
    myFixture.addClass(
      """|class One {
         |  static class Two {
         |    static class Foo extends Two {}
         |    static class Bar extends Two {}
         |  }
         |}""".stripMargin)

    myFixture.configureByText("Test.scala",
      """
        |import One.Two
        |
        |object Test {
        |  val x = if (true) new Two.Foo() else new Two.Bar()
        |  val y: Two = x
        |}
        |""".stripMargin)

    myFixture.testHighlighting(false, false, false, getFile.getVirtualFile)
  }
}

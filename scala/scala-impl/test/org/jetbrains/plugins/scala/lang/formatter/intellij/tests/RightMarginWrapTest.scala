package org.jetbrains.plugins.scala.lang.formatter.intellij.tests

import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase

class RightMarginWrapTest extends AbstractScalaFormatterTestBase {
  def testSCL12314(): Unit = {
    getCommonSettings.WRAP_LONG_LINES = true
    (getSettings.WRAP_LONG_LINES = true)
    val before =
      """
        |class Test {
        |  def aFun(a1: String, a2: String, a3: String, a4: String, a5: String, a6: String, a7: String, a8: String, a9: String, a10: String) = ???
        |}
      """.stripMargin
    val after =
      """
        |class Test {
        |  def aFun(a1: String, a2: String, a3: String, a4: String, a5: String, a6: String, a7: String, a8: String,
        |           a9: String, a10: String) = ???
        |}
      """.stripMargin
    doTextTest(before, after)
  }
}

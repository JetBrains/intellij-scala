package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class VarArgsInCurlyBracesTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass: Boolean = false

  def testSCL3856(): Unit = {
    val code =
      """
        |val array = Array[String]()
        |Set.apply[String]{ array: _* }
      """.stripMargin
    checkTextHasNoErrors(code)
  }
}

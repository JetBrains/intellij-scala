package org.jetbrains.plugins.scala.codeInsight.intention.stringLiteral

import org.jetbrains.plugins.scala.codeInsight.intentions

class ConvertToInterpolatedActionTest extends intentions.ScalaIntentionTestBase {

  override def familyName: String = FormatConversionIntention.ConvertToInterpolated

  def test_SCL_5275(): Unit =
    doTest(
      """val a = <caret>"http://%s:%d/more".format(addr, port)""".stripMargin,
      """val a = s"http://$addr:$port/more"""".stripMargin,
    )

  def test_SCL_5275_1(): Unit =
    doTest(
      """<caret>"%s / %s".format(1, 2)""".stripMargin,
      """"1 / 2"""".stripMargin,
    )

  def test_SCL_5275_2(): Unit =
    doTest(
      """<caret>"%s / %s / %s".format(1, 2, x)""".stripMargin,
      """s"1 / 2 / $x"""".stripMargin,
    )

  def test_SCL_5386(): Unit =
    doTest(
      """val x = 1
        |<caret>"%sfoo".format(x)""".stripMargin,
      """val x = 1
        |s"${x}foo" """.stripMargin,
    )
}

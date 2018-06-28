package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_12}
import org.jetbrains.plugins.scala.util.TestUtils

class LiteralTypesHightlightingTest_2_12 extends LiteralTypesHighlightingTestBase {

  override implicit val version: ScalaVersion = Scala_2_12

  override def folderPath = TestUtils.getTestDataPath + "/annotator/literalTypes/"

  private def messageNoSupport(typeText: String): String = ScalaBundle.message("wrong.type.no.literal.types", typeText)

  def testDefaultIsOff(): Unit = {
    val expectedErrors =
      Error("-1", messageNoSupport("-1")) ::
      Error("1", messageNoSupport("1")) :: Nil

    doTest(expectedErrors,
      fileText = Some("""
        |class O {
        |  val x: -1 = -1
        |  1: 1
        |}
      """.stripMargin))
  }

  def testSimple(): Unit = doTest(settingOn = true)
}

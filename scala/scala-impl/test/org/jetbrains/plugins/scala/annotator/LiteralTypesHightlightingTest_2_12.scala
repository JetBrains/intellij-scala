package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_12}
import org.jetbrains.plugins.scala.util.TestUtils

class LiteralTypesHightlightingTest_2_12 extends LiteralTypesHighlightingTestBase {

  override implicit val version: ScalaVersion = Scala_2_12

  override def folderPath = TestUtils.getTestDataPath + "/annotator/literalTypes/"

  def testDefaultIsOff(): Unit = doTest(fileText = Some(
    """
      |class O {
      |  val x: -1 = -1
      |  1: 1
      |}
    """.stripMargin), errorsFun = {
    case Error("-1", "Wrong type '-1', for literal types support please use '-Yliteral-types' compiler flag") ::
      Error("1", "Wrong type '1', for literal types support please use '-Yliteral-types' compiler flag") ::
      Nil => })

  def testSimple(): Unit = doTest(settingOn = true)
}

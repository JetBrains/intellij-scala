package org.jetbrains.plugins.scala
package annotator

class LiteralTypesHighlightingTest_without_LiteralTypesSupport extends LiteralTypesHighlightingTestBase {
  import Message._

  override protected def supportedIn(version: ScalaVersion): Boolean = version < LatestScalaVersions.Scala_2_13

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

  def testSupportedWithFlag(): Unit = {
    val fileText = Some {
      """
        |object SimpleTest {
        |  val v: 42 = 42
        |}"""
        .stripMargin
    }
    doTest(fileText = fileText, settingOn = true)
  }
}

package org.jetbrains.plugins.scala.conversion.copy

class CopyScalaToScalaTest extends CopyPasteTestBase {

  def testSemicolon(): Unit = {
    val from =
      s"""object Test {
         |  val x = 1$Start;$End
         |}""".stripMargin
    val to =
      s"""object Test2 {
         |  1$Caret
         |}""".stripMargin
    val after =
      """object Test2 {
        |  1;
        |}""".stripMargin

    doTest(from, to, after)
  }

  def testCommaSeparated(): Unit = {
    val from = s"(${Start}1, 2, 3$End)"
    val to =
      s"""object Test {
         |  ($Caret)
         |}""".stripMargin
    val after =
      """object Test {
        |  (1, 2, 3)
        |}""".stripMargin

    doTest(from, to, after)
  }
}
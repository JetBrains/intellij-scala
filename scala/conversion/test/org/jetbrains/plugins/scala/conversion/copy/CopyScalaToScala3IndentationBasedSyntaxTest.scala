package org.jetbrains.plugins.scala.conversion.copy

class CopyScalaToScala3IndentationBasedSyntaxTest extends CopyPasteTestBase {

  def testExtension(): Unit = {
    val from =
      s"""$Start
         |case class Circle(x: Double, y: Double, radius: Double)
         |
         |extension (c: Circle)
         |  def circumference: Double = c.radius * math.Pi * 2$End
         |""".stripMargin
    val to =
      s"""object Test2 {
         |  $Caret
         |}""".stripMargin
    val after =
      """object Test2 {
        |
        |  case class Circle(x: Double, y: Double, radius: Double)
        |
        |  extension (c: Circle)
        |    def circumference: Double = c.radius * math.Pi * 2
        |}""".stripMargin

    doTest(from, to, after)
  }

}

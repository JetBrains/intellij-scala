package org.jetbrains.plugins.scala.conversion.copy

class CopyScalaToScalaTest extends CopyPasteTestBase {

  def testSemicolon(): Unit = {
    val from =
      s"""object Test {
         |  val x = 1$Start;$End
         |}"""
    val to =
      s"""object Test2 {
         |  1$Caret
         |}"""
    val after =
      """object Test2 {
        |  1;
        |}"""

    doTestWithStrip(from, to, after)
  }

  def testCommaSeparated(): Unit = {
    val from = s"(${Start}1, 2, 3$End)"
    val to =
      s"""object Test {
         |  ($Caret)
         |}"""
    val after =
      """object Test {
        |  (1, 2, 3)
        |}"""

    doTestWithStrip(from, to, after)
  }

  def testExtension(): Unit = {
    val from =
      s"""${Start}case class Circle(x: Double, y: Double, radius: Double)
         |
         |extension (c: Circle)
         |  def circumference: Double = c.radius * math.Pi * 2$End
         |"""
    val to =
      s"""object Example {
         |  $Caret
         |}"""
    val after =
      """object Example {
        |  case class Circle(x: Double, y: Double, radius: Double)
        |
        |  extension (c: Circle)
        |    def circumference: Double = c.radius * math.Pi * 2
        |}"""

    doTestWithStrip(from, to, after)
  }

  def testExtension_1(): Unit = {
    val from =
      s"""${Start}case class Circle(x: Double, y: Double, radius: Double)
         |
         |extension (c: Circle)
         |  def circumference: Double = c.radius * math.Pi * 2$End
         |"""
    val to =
      s"""object Example {
         |$Caret
         |}"""
    val after =
      """object Example {
        |case class Circle(x: Double, y: Double, radius: Double)
        |
        |extension (c: Circle)
        |  def circumference: Double = c.radius * math.Pi * 2
        |}"""

    doTestWithStrip(from, to, after)
  }

  def testExtension_2(): Unit = {
    val from =
      s"""$Start
         |case class Circle(x: Double, y: Double, radius: Double)
         |
         |extension (c: Circle)
         |  def circumference: Double = c.radius * math.Pi * 2
         |$End
         |"""
    val to =
      s"""object Example {
         |  $Caret
         |}"""
    val after =
      """object Example {
        |
        |  case class Circle(x: Double, y: Double, radius: Double)
        |
        |  extension (c: Circle)
        |    def circumference: Double = c.radius * math.Pi * 2
        |
        |}"""

    doTestWithStrip(from, to, after)
  }

  def testExtension_3(): Unit = {
    val from =
      s"""$Start
         |case class Circle(x: Double, y: Double, radius: Double)
         |
         |extension (c: Circle)
         |  def circumference: Double = c.radius * math.Pi * 2
         |$End
         |"""
    val to =
      s"""object Example {
         |$Caret
         |}"""
    val after =
      """object Example {
        |
        |case class Circle(x: Double, y: Double, radius: Double)
        |
        |extension (c: Circle)
        |  def circumference: Double = c.radius * math.Pi * 2
        |
        |}"""

    doTestWithStrip(from, to, after)
  }
}
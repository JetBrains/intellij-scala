package org.jetbrains.plugins.scala.conversion.copy

import org.jetbrains.plugins.scala.ScalaVersion

class CopyScalaToScala3IndentationBasedSyntaxTest extends CopyPasteTestBase {

  // copied from Scala3IndentationBasedSyntaxBackspaceTest
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

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

  def testInnerMethod(): Unit = {
    val from =
      s"""${Start}def foo() =
         |  def baz() =
         |    print(1)
         |  baz(1)$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz(1)
         |"""

    doTestWithStrip(from, to, after)
  }

  def testInnerMethod_1(): Unit = {
    val from =
      s"""${Start}def foo() =
         |  def baz() =
         |    print(1)
         |  baz(1)$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |$Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz(1)
         |"""

    doTestWithStrip(from, to, after)
  }

  def testInnerMethod_2(): Unit = {
    val from =
      s"""$Start
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz(1)
         |$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz(1)
         |
         |"""

    doTestWithStrip(from, to, after)
  }

  def testInnerMethod_3(): Unit = {
    val from =
      s"""$Start
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz(1)
         |$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |$Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz(1)
         |
         |"""

    doTestWithStrip(from, to, after)
  }

  def testInnerMethod_4(): Unit = {
    val from =
      s"""object Example:
         |$Start  def foo() =
         |    def baz() =
         |      print(1)
         |    baz(1)$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz(1)
         |"""

    doTestWithStrip(from, to, after)
  }

  def testInnerMethod_5(): Unit = {
    val from =
      s"""object Example:
         |$Start  def foo() =
         |    def baz() =
         |      print(1)
         |    baz(1)$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |$Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz(1)
         |"""

    doTestWithStrip(from, to, after)
  }

  def testInnerMethod_6(): Unit = {
    val from =
      s"""object Example:
         |$Start
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz(1)
         |$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz(1)
         |
         |"""

    doTestWithStrip(from, to, after)
  }

  def testInnerMethod_7(): Unit = {
    val from =
      s"""object Example:
         |$Start
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz(1)
         |$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |$Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz(1)
         |
         |"""

    doTestWithStrip(from, to, after)
  }
}

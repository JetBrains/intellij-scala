package org.jetbrains.plugins.scala.lang.actions.editor.copy

import org.jetbrains.plugins.scala.ScalaVersion

class CopyScalaToScala3IndentationBasedSyntaxTest extends CopyPasteTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

  def testWhitespace(): Unit = {
    val from =
      s"""$Start  $empty
         |$End"""
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |    $empty
         |
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testSingleLine(): Unit = {
    val from =
      s"""$Start  ???
         |$End"""
    val to =
      s"""def bar() =
         |  print(2)
         |$Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |  ???
         |
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testMultiLine(): Unit = {
    val from =
      s"""$Start
         |  ???
         |$End"""
    val to =
      s"""def bar() =
         |  print(2)
         |$Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |
         |???
         |
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testMultiLine_Infix(): Unit = {
    val from =
      s"""object Example:
         |  def foo() =
         |    ${Start}if false then 1 else
         |      2
         |      3$End
         |"""
    val to =
      s"""def bar() =
         |  print($Caret)
         |"""
    val after =
      s"""def bar() =
         |  print(if false then 1 else
         |    2
         |    3)
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  // SCL-20036
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
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testExtension_1(): Unit = {
    val from =
      s"""${Start}case class Circle(x: Double, y: Double, radius: Double)
         |
         |extension (c: Circle)
         |  def circumference: Double = c.radius * math.Pi * 2$End
         |"""
    val to =
      s"""object Example:
         |  $Caret
         |"""
    val after =
      """object Example:
        |  case class Circle(x: Double, y: Double, radius: Double)
        |
        |  extension (c: Circle)
        |    def circumference: Double = c.radius * math.Pi * 2
        |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testExtension_2(): Unit = {
    val from =
      s"""${Start}case class Circle(x: Double, y: Double, radius: Double)
         |
         |extension (c: Circle)
         |  def circumference: Double = c.radius * math.Pi * 2$End
         |"""
    val to =
      s"""object Example:
         |$Caret
         |"""
    val after =
      """object Example:
        |case class Circle(x: Double, y: Double, radius: Double)
        |
        |extension (c: Circle)
        |  def circumference: Double = c.radius * math.Pi * 2
        |"""
    doTestWithStripWithAllSelections(from, to, after)
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
      s"""object Example:
         |  $Caret
         |"""
    val after =
      """object Example:
        |
        |  case class Circle(x: Double, y: Double, radius: Double)
        |
        |  extension (c: Circle)
        |    def circumference: Double = c.radius * math.Pi * 2
        |
        |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod(): Unit = {
    val from =
      s"""${Start}def foo() =
         |  def baz() =
         |    print(1)
         |  baz()$End
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
         |    baz()
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_1(): Unit = {
    val from =
      s"""${Start}def foo() =
         |  def baz() =
         |    print(1)
         |  baz()$End
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
         |  baz()
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_2(): Unit = {
    val from =
      s"""$Start
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz()
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
         |    baz()
         |
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_3(): Unit = {
    val from =
      s"""$Start
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz()
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
         |  baz()
         |
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject(): Unit = {
    val from =
      s"""object Example:
         |$Start  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
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
         |    baz()
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_1(): Unit = {
    val from =
      s"""object Example:
         | $Start def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
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
         |  baz()
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_2(): Unit = {
    val from =
      s"""object Example:
         |  ${Start}def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
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
         |    baz()
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_3(): Unit = {
    val from =
      s"""object Example:
         |  ${Start}def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
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
         |  baz()
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_4(): Unit = {
    val from =
      s"""object Example:
         |$Start
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()
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
         |    baz()
         |
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_5(): Unit = {
    val from =
      s"""object Example:
         |$Start
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()
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
         |  baz()
         |
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_Braces(): Unit = {
    val from =
      s"""${Start}def foo() = {
         |  def baz() =
         |    print(1)
         |  baz(1)
         |}$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() = {
         |    def baz() =
         |      print(1)
         |    baz(1)
         |  }
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_Braces_1(): Unit = {
    val from =
      s"""$Start
         |def foo() = {
         |  def baz() =
         |    print(1)
         |  baz(1)
         |}
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
         |  def foo() = {
         |    def baz() =
         |      print(1)
         |    baz(1)
         |  }
         |
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_Braces_FromObject(): Unit = {
    val from =
      s"""object Example {
         |$Start  def foo() = {
         |    def baz() =
         |      print(1)
         |    baz(1)
         |  }$End
         |}
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() = {
         |    def baz() =
         |      print(1)
         |    baz(1)
         |  }
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_Braces_FromObject_1(): Unit = {
    val from =
      s"""object Example {
         |$Start
         |  def foo() = {
         |    def baz() =
         |      print(1)
         |    baz(1)
         |  }
         |$End}
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
         |  def foo() = {
         |    def baz() =
         |      print(1)
         |    baz(1)
         |  }
         |
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_EOF(): Unit = {
    val from =
      s"""object Example:
         |  ${Start}def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret"""
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_EOF_1(): Unit = {
    val from =
      s"""object Example:
         |  ${Start}def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |$Caret"""
    val after =
      s"""def bar() =
         |  print(2)
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz()"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_Postfix(): Unit = {
    val from =
      s"""object Example:
         |  ${Start}def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret???
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()???
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_InBlock(): Unit = {
    val from =
      s"""object Example:
         |  ${Start}def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |    ???
         |  ???
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()
         |    ???
         |  ???
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_Comment(): Unit = {
    val from =
      s"""object Example:
         |  /* foo */${Start}def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |  ???
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()
         |  ???
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_Comment_1(): Unit = {
    val from =
      s"""object Example:
         |/* foo */  ${Start}def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |  ???
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()
         |  ???
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  // TODO ignored
  def _testInnerMethod_FromObject_Comment_2(): Unit = {
    val from =
      s"""object Example:
         |  ${Start}def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |/* foo */  $Caret
         |  ???
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |/* foo */  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()
         |  ???
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_Comment_3(): Unit = {
    val from =
      s"""object Example:
         |  ${Start}def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |  /* foo */$Caret
         |  ???
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |  /* foo */ def foo() =
         |    def baz() =
         |      print(1)
         |    baz()
         |  ???
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  // TODO ignored
  def _testInnerMethod_FromObject_Comment_4(): Unit = {
    val from =
      s"""object Example:
         |  ${Start}def foo() =
         | /* foo */   def baz() =
         |      print(1)
         |    baz()$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |  ???
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() =
         | /* foo */   def baz() =
         |      print(1)
         |    baz()
         |  ???
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_Tabs(): Unit = {
    val from =
      s"""object Example:
         |$tab${Start}def foo() =
         |$tab${tab}def baz() =
         |$tab$tab${tab}print(1)
         |$tab${tab}baz()
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
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()
         |
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  // TODO ignored
  def _testLesserIndentation(): Unit = {
    val from =
      s"""object A:
         |  def foo() =
         |    ${Start}1
         |  2$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |  1
         |2
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }

  // TODO ignored
  def _testLesserIndentation_Tabs(): Unit = {
    val from =
      s"""object A:
         |${tab}def foo() =
         |$tab$tab${Start}1
         |${tab}2$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |  1
         |2
         |"""
    doTestWithStripWithAllSelections(from, to, after)
  }
}

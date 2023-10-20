package org.jetbrains.plugins.scala.lang.actions.editor.copy

import com.intellij.application.options.CodeStyle
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaVersion}

class CopyScalaToScala3IndentationBasedSyntaxTest extends CopyPasteTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

  def testWhitespace(): Unit = {
    val from =
      s"""$Start  $empty
         |$End""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |    $empty
         |
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testSingleLine(): Unit = {
    val from =
      s"""$Start  ???
         |$End""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |$Caret
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |  ???
         |
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testMultiLine(): Unit = {
    val from =
      s"""$Start
         |  ???
         |$End""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |$Caret
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |
         |???
         |
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testMultiLine_Infix(): Unit = {
    val from =
      s"""object Example:
         |  def foo() =
         |    ${Start}if false then 1 else
         |      2
         |      3$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print($Caret)
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(if false then 1 else
         |    2
         |    3)
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  // SCL-20036
  def testExtension(): Unit = {
    val from =
      s"""${Start}case class Circle(x: Double, y: Double, radius: Double)
         |
         |extension (c: Circle)
         |  def circumference: Double = c.radius * math.Pi * 2$End
         |""".stripMargin
    val to =
      s"""object Example {
         |  $Caret
         |}""".stripMargin
    val after =
      """object Example {
        |  case class Circle(x: Double, y: Double, radius: Double)
        |
        |  extension (c: Circle)
        |    def circumference: Double = c.radius * math.Pi * 2
        |}""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testExtension_1(): Unit = {
    val from =
      s"""${Start}case class Circle(x: Double, y: Double, radius: Double)
         |
         |extension (c: Circle)
         |  def circumference: Double = c.radius * math.Pi * 2$End
         |""".stripMargin
    val to =
      s"""object Example:
         |  $Caret
         |""".stripMargin
    val after =
      """object Example:
        |  case class Circle(x: Double, y: Double, radius: Double)
        |
        |  extension (c: Circle)
        |    def circumference: Double = c.radius * math.Pi * 2
        |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testExtension_2(): Unit = {
    val from =
      s"""${Start}case class Circle(x: Double, y: Double, radius: Double)
         |
         |extension (c: Circle)
         |  def circumference: Double = c.radius * math.Pi * 2$End
         |""".stripMargin
    val to =
      s"""object Example:
         |$Caret
         |""".stripMargin
    val after =
      """object Example:
        |  case class Circle(x: Double, y: Double, radius: Double)
        |
        |  extension (c: Circle)
        |    def circumference: Double = c.radius * math.Pi * 2
        |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testExtension_3(): Unit = {
    val from =
      s"""$Start
         |case class Circle(x: Double, y: Double, radius: Double)
         |
         |extension (c: Circle)
         |  def circumference: Double = c.radius * math.Pi * 2
         |$End
         |""".stripMargin
    val to =
      s"""object Example:
         |  $Caret
         |""".stripMargin
    val after =
      """object Example:
        |
        |  case class Circle(x: Double, y: Double, radius: Double)
        |
        |  extension (c: Circle)
        |    def circumference: Double = c.radius * math.Pi * 2
        |
        |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testInnerMethod(): Unit = {
    val from =
      s"""${Start}def foo() =
         |  def baz() =
         |    print(1)
         |  baz()$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testInnerMethod_1(): Unit = {
    val from =
      s"""${Start}def foo() =
         |  def baz() =
         |    print(1)
         |  baz()$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |$Caret
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz()
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testInnerMethod_2(): Unit = {
    val from =
      s"""$Start
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz()
         |$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()
         |
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testInnerMethod_3(): Unit = {
    val from =
      s"""$Start
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz()
         |$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |$Caret
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz()
         |
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject(): Unit = {
    val from =
      s"""object Example:
         |$Start  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_1(): Unit = {
    val from =
      s"""object Example:
         | $Start def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |$Caret
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz()
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_2(): Unit = {
    val from =
      s"""object Example:
         |  ${Start}def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_3(): Unit = {
    val from =
      s"""object Example:
         |  ${Start}def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |$Caret
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz()
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
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
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()
         |
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
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
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |$Caret
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz()
         |
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testInnerMethod_Braces(): Unit = {
    val from =
      s"""${Start}def foo() = {
         |  def baz() =
         |    print(1)
         |  baz(1)
         |}$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() = {
         |    def baz() =
         |      print(1)
         |    baz(1)
         |  }
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
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
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |""".stripMargin
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
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
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
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() = {
         |    def baz() =
         |      print(1)
         |    baz(1)
         |  }
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
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
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |""".stripMargin
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
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_EOF(): Unit = {
    val from =
      s"""object Example:
         |  ${Start}def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_EOF_1(): Unit = {
    val from =
      s"""object Example:
         |  ${Start}def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |$Caret""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz()""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_Postfix(): Unit = {
    val from =
      s"""object Example:
         |  ${Start}def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret???
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()???
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_InBlock(): Unit = {
    val from =
      s"""object Example:
         |  ${Start}def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |    ???
         |  ???
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()
         |    ???
         |  ???
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_Comment(): Unit = {
    val from =
      s"""object Example:
         |  /* foo */${Start}def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |  ???
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()
         |  ???
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_Comment_1(): Unit = {
    val from =
      s"""object Example:
         |/* foo */  ${Start}def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |  ???
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()
         |  ???
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  // TODO ignored
  def _testInnerMethod_FromObject_Comment_2(): Unit = {
    val from =
      s"""object Example:
         |  ${Start}def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |/* foo */  $Caret
         |  ???
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |/* foo */  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()
         |  ???
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_Comment_3(): Unit = {
    val from =
      s"""object Example:
         |  ${Start}def foo() =
         |    def baz() =
         |      print(1)
         |    baz()$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |  /* foo */$Caret
         |  ???
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |  /* foo */ def foo() =
         |    def baz() =
         |      print(1)
         |    baz()
         |  ???
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  // TODO ignored
  def _testInnerMethod_FromObject_Comment_4(): Unit = {
    val from =
      s"""object Example:
         |  ${Start}def foo() =
         | /* foo */   def baz() =
         |      print(1)
         |    baz()$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |  ???
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() =
         | /* foo */   def baz() =
         |      print(1)
         |    baz()
         |  ???
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testInnerMethod_FromObject_Tabs(): Unit = {
    val from =
      s"""object Example:
         |$tab${Start}def foo() =
         |$tab${tab}def baz() =
         |$tab$tab${tab}print(1)
         |$tab${tab}baz()
         |$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz()
         |
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testLesserIndentation(): Unit = {
    val from =
      s"""object A:
         |  def foo() =
         |    ${Start}1
         |  2$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |  1
         |2
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testLesserIndentation_Tabs(): Unit = {
    val from =
      s"""object A:
         |${tab}def foo() =
         |$tab$tab${Start}1
         |${tab}2$End
         |""".stripMargin
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |""".stripMargin
    val after =
      s"""def bar() =
         |  print(2)
         |  1
         |2
         |""".stripMargin
    doTestWithAllSelections(from, to, after)
  }

  def testPasteInTheMiddleOfTheClassBody_CaretUnindented(): Unit = {
    doTestWithAllSelections(
      s"""object Source:
         |  ${Start}def hello(): Unit =
         |    println("Hello, world!")$End""".stripMargin,
      s"""object Target:
         |  def m0 = ???
         |
         |$Caret
         |
         |  def m1 = ???""".stripMargin,
      s"""object Target:
         |  def m0 = ???
         |
         |  def hello(): Unit =
         |    println("Hello, world!")$Caret
         |
         |  def m1 = ???""".stripMargin,
    )
  }

  def testPasteInTheMiddleOfTheClassBody_CaretUnindented_Nested(): Unit = {
    doTestWithAllSelections(
      s"""object Source:
         |  ${Start}def hello(): Unit =
         |    println("Hello, world!")$End""".stripMargin,
      s"""object Wrapper:
         |  object Target:
         |    def m0 = ???
         |
         |  $Caret
         |
         |    def m1 = ???""".stripMargin,
      s"""object Wrapper:
         |  object Target:
         |    def m0 = ???
         |
         |    def hello(): Unit =
         |      println("Hello, world!")$Caret
         |
         |    def m1 = ???""".stripMargin,
    )
  }

  def testPasteInTheMiddleOfTheClassBody_CaretUnindented_WiderSourceSelection(): Unit = {
    doTestWithAllSelections(
      s"""object Source:
         |  ${Start}def hello(): Unit =
         |    println("Hello, world!")    $End""".stripMargin,
      s"""object Target:
         |  def m0 = ???
         |
         |$Caret
         |
         |  def m1 = ???""".stripMargin,
      s"""object Target:
         |  def m0 = ???
         |
         |  def hello(): Unit =
         |    println("Hello, world!")$Caret
         |
         |  def m1 = ???""".stripMargin,
    )
  }

  def testPasteInTheMiddleOfTheClassBody_CaretIndentedFar(): Unit = {
    doTestWithAllSelections(
      s"""object Source:
         |  ${Start}def hello(): Unit =
         |    println("Hello, world!")    $End""".stripMargin,
      s"""object Target:
         |  def m0 = ???
         |
         |      $Caret
         |
         |  def m1 = ???""".stripMargin,
      s"""object Target:
         |  def m0 = ???
         |
         |  def hello(): Unit =
         |    println("Hello, world!")$Caret
         |
         |  def m1 = ???""".stripMargin,
    )
  }

  def testPasteToEmptyPendingClassBody_ShouldRespectIndentationSettings(): Unit = {
    CodeStyle.getSettings(getProject).getIndentOptions(ScalaFileType.INSTANCE).INDENT_SIZE = 5
    doTestWithAllSelections(
      s"""object Source:
         |  ${Start}def hello(): Unit =
         |    println("Hello, world!")$End""".stripMargin,
      s"""object Target:
         |$Caret
         |object Target2
         |""".stripMargin,
      s"""object Target:
         |     def hello(): Unit =
         |          println("Hello, world!")$Caret
         |object Target2
         |""".stripMargin,
    )
  }

  def testPasteToEmptyPendingClassBody_WithExtraComment(): Unit = {
    doTestWithAllSelections(
      s"""object Source:
         |  ${Start}def hello(): Unit =
         |    println("Hello, world!")$End""".stripMargin,
      s"""object Target: //line comment
         |$Caret
         |object Target2
         |""".stripMargin,
      s"""object Target: //line comment
         |  def hello(): Unit =
         |    println("Hello, world!")$Caret
         |object Target2
         |""".stripMargin,
    )
  }

  def testPasteToEmptyPendingClassBody_Nested(): Unit = {
    doTestWithAllSelections(
      s"""object Source:
         |  ${Start}def hello(): Unit =
         |    println("Hello, world!")$End""".stripMargin,
      s"""object Wrapper:
         |  object Target:
         |  $Caret
         |  object Target2
         |""".stripMargin,
      s"""object Wrapper:
         |  object Target:
         |    def hello(): Unit =
         |      println("Hello, world!")$Caret
         |  object Target2
         |""".stripMargin,
    )
  }

  def testPasteToEmptyPendingClassBody_Nested_WithExtraSelection(): Unit = {
    doTestWithAllSelections(
      s"""object Source:
         |$Start  def hello(): Unit =
         |    println("Hello, world!")   $End""".stripMargin,
      s"""object Wrapper:
         |  object Target:
         |  $Caret
         |  object Target2
         |""".stripMargin,
      s"""object Wrapper:
         |  object Target:
         |    def hello(): Unit =
         |      println("Hello, world!")$Caret
         |  object Target2
         |""".stripMargin,
    )
  }

  def testPasteToEmptyPendingClassBody_LastInFile(): Unit = {
    doTestWithAllSelections(
      s"""object Source:
         |  ${Start}def hello(): Unit =
         |    println("Hello, world!")$End""".stripMargin,
      s"""object Target:
         |$Caret
         |""".stripMargin,
      s"""object Target:
         |  def hello(): Unit =
         |    println("Hello, world!")$Caret
         |""".stripMargin,
    )
  }

  def testPasteToEmptyPendingClassBody_LastInFile_Nested(): Unit = {
    doTestWithAllSelections(
      s"""object Source:
         |  ${Start}def hello(): Unit =
         |    println("Hello, world!")$End""".stripMargin,
      s"""object Wrapper:
         |  object Target:
         |  $Caret
         |""".stripMargin,
      s"""object Wrapper:
         |  object Target:
         |    def hello(): Unit =
         |      println("Hello, world!")$Caret
         |""".stripMargin,
    )
  }

  def testPasteToEmptyPendingExtensionBody(): Unit = {
    doTestWithAllSelections(
      s"""object Source:
         |  ${Start}def hello(): Unit =
         |    println("Hello, world!")$End""".stripMargin,
      s"""extension (s: String)
         |$Caret
         |class other
         |""".stripMargin,
      s"""extension (s: String)
         |  def hello(): Unit =
         |    println("Hello, world!")$Caret
         |class other
         |""".stripMargin,
    )
  }

  def testPasteToEmptyPendingMethodBody(): Unit = {
    doTestWithAllSelections(
      s"""object Source:
         |  ${Start}1
         |  2
         |  3$End""".stripMargin,
      s"""def foo =
         |$Caret
         |class other
         |""".stripMargin,
      s"""def foo =
         |  1
         |  2
         |  3$Caret
         |class other
         |""".stripMargin,
    )
  }

  def testPasteToEmptyPendingMethodBody_Nested(): Unit = {
    doTestWithAllSelections(
      s"""object Source:
         |  ${Start}1
         |  2
         |  3$End""".stripMargin,
      s"""def bar =
         |  def foo =
         |$Caret
         |class other
         |""".stripMargin,
      s"""def bar =
         |  def foo =
         |    1
         |    2
         |    3$Caret
         |class other
         |""".stripMargin,
    )
  }
}

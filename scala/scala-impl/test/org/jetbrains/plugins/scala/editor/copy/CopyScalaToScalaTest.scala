package org.jetbrains.plugins.scala.editor.copy

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

    doTestWithStripWithAllSelections(from, to, after)
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

    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_Braces(): Unit = {
    val from =
      s"""${Start}def foo() = {
         |  def baz() =
         |    print(1)
         |  baz()
         |}$End
         |"""
    val to =
      s"""def bar() = {
         |  print(2)
         |  $Caret
         |}
         |"""
    val after =
      s"""def bar() = {
         |  print(2)
         |  def foo() = {
         |    def baz() =
         |      print(1)
         |    baz()
         |  }
         |}
         |"""

    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_Braces_1(): Unit = {
    val from =
      s"""${Start}def foo() = {
         |  def baz() =
         |    print(1)
         |  baz()
         |}$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |$Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |def foo() = {
         |  def baz() =
         |    print(1)
         |  baz()
         |}
         |"""

    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_Braces_2(): Unit = {
    val from =
      s"""$Start
         |def foo() = {
         |  def baz() =
         |    print(1)
         |  baz()
         |}
         |$End
         |"""
    val to =
      s"""def bar() = {
         |  print(2)
         |  $Caret
         |}
         |"""
    val after =
      s"""def bar() = {
         |  print(2)
         |
         |  def foo() = {
         |    def baz() =
         |      print(1)
         |    baz()
         |  }
         |
         |}
         |"""

    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_Braces_3(): Unit = {
    val from =
      s"""$Start
         |def foo() = {
         |  def baz() =
         |    print(1)
         |  baz()
         |}
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
         |def foo() = {
         |  def baz() =
         |    print(1)
         |  baz()
         |}
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
         |    baz()
         |  }$End
         |}
         |"""
    val to =
      s"""def bar() = {
         |  print(2)
         |  $Caret
         |}
         |"""
    val after =
      s"""def bar() = {
         |  print(2)
         |  def foo() = {
         |    def baz() =
         |      print(1)
         |    baz()
         |  }
         |}
         |"""

    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_Braces_FromObject_1(): Unit = {
    val from =
      s"""object Example {
         |$Start  def foo() = {
         |    def baz() =
         |      print(1)
         |    baz()
         |  }$End
         |}
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |$Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |def foo() = {
         |  def baz() =
         |    print(1)
         |  baz()
         |}
         |"""

    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_Braces_FromObject_2(): Unit = {
    val from =
      s"""object Example {
         |$Start
         |  def foo() = {
         |    def baz() =
         |      print(1)
         |    baz()
         |  }
         |$End}
         |"""
    val to =
      s"""def bar() = {
         |  print(2)
         |  $Caret
         |}
         |"""
    val after =
      s"""def bar() = {
         |  print(2)
         |
         |  def foo() = {
         |    def baz() =
         |      print(1)
         |    baz()
         |  }
         |
         |}
         |"""

    doTestWithStripWithAllSelections(from, to, after)
  }

  def testInnerMethod_Braces_FromObject_3(): Unit = {
    val from =
      s"""object Example {
         |$Start
         |  def foo() = {
         |    def baz() =
         |      print(1)
         |    baz()
         |  }
         |$End}
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
         |def foo() = {
         |  def baz() =
         |    print(1)
         |  baz()
         |}
         |
         |"""

    doTestWithStripWithAllSelections(from, to, after)
  }
}
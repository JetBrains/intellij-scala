package org.jetbrains.plugins.scala.lang.refactoring

class ScalaStatementMoverTest extends StatementMoverTestBase {

  def testSingleLineMember(): Unit = {
    s"${|}def a".moveUpIsDisabled()
    s"${|}def a".moveDownIsDisabled()

    s"${|}def a\ndef b\n".moveUpIsDisabled()
    s"def a\n${|}def b\n" movedUpIs "def b\ndef a\n"

    s"${|}def a\ndef b" movedDownIs "def b\ndef a\n"
    s"def a\n${|}def b".moveDownIsDisabled()

    s"${|}def a\ndef b\ndef c".moveUpIsDisabled()
    s"def a\n${|}def b\ndef c" movedUpIs "def b\ndef a\ndef c"
    s"def a\ndef b\n${|}def c" movedUpIs "def a\ndef c\ndef b\n"

    s"${|}def a\ndef b\ndef c" movedDownIs "def b\ndef a\ndef c"
    s"def a\n${|}def b\ndef c" movedDownIs "def a\ndef c\ndef b\n"
    s"def a\ndef b\n${|}def c".moveDownIsDisabled()
  }

  def testCursorPositioning(): Unit = {
    s"${|}def a\ndef b" movedDownIs "def b\ndef a\n"
    s"def${|} a\ndef b" movedDownIs "def b\ndef a\n"
    s"def a${|}\ndef b" movedDownIs "def b\ndef a\n"
    s"def a {${|}}\ndef b {}" movedDownIs "def b {}\ndef a {}\n"
    s"def a {}${|}\ndef b {}" movedDownIs "def b {}\ndef a {}\n"
  }

  def testCursorLinePositioning(): Unit = {
    s"def a {\n${|}\n}\ndef b {\n\n}".moveDownIsDisabled()
  }

  def testLineSpace(): Unit = {
    s"def a\n\n${|}def b" movedUpIs "def b\n\ndef a\n"
    s"${|}def a\n\ndef b" movedDownIs "def b\n\ndef a\n"
  }

  def testExpressionAsSource(): Unit = {
    s"${|}v = 1\ndef a".moveDownIsDisabled()
  }

  def testExpressionAsTarget(): Unit = {
    s"${|}def a\nv = 1\ndef b" movedDownIs "v = 1\ndef a\ndef b"
  }

  def testSkipComment(): Unit = {
    s"${|}def a\n//comment\n\ndef b\n" movedDownIs "def b\n//comment\n\ndef a\n"
  }

  def testSourceComment(): Unit = {
    s"//source\n${|}def a\ndef b" movedDownIs "def b\n//source\ndef a\n"
    s"//source 1\n//source 2\n${|}def a\ndef b" movedDownIs "def b\n//source 1\n//source 2\ndef a\n"
    s"//foo\n\n//source\n${|}def a\ndef b" movedDownIs "//foo\n\ndef b\n//source\ndef a\n"
  }

  def testDestinationComment(): Unit = {
    s"//source\ndef a\n${|}def b" movedUpIs "def b\n//source\ndef a\n"
    s"//source 1\n//source 2\ndef a\n${|}def b" movedUpIs "def b\n//source 1\n//source 2\ndef a\n"
    s"//foo\n\n//source\ndef a\n${|}def b" movedUpIs "//foo\n\ndef b\n//source\ndef a\n"
  }

  def testMultipleLinesMember(): Unit = {
    s"def a {\n// method a\n}\n\n${|}def b {\n// method b\n}" movedUpIs "def b {\n  // method b\n}\n\ndef a {\n// method a\n}\n"
    s"${|}def a {\n// method a\n}\n\ndef b {\n// method b\n}" movedDownIs "def b {\n// method b\n}\n\ndef a {\n  // method a\n}\n"

    s"${|}def a\n\ndef b {\n// method b\n}" movedDownIs "def b {\n// method b\n}\n\ndef a\n"
    s"${|}def a {\n// method a\n}\n\ndef b" movedDownIs "def b\n\ndef a {\n  // method a\n}\n"
  }

  def testCaseClause(): Unit = {
    //    s"1 match {\n${|}case 1 =>\ncase 2 =>\ncase 3 =>\n}" moveUpIsDisabled;
    s"1 match {\n${|}case 1 =>\ncase 2 =>\ncase 3 =>\n}" movedDownIs "1 match {\ncase 2 =>\ncase 1 =>\ncase 3 =>\n}"

    s"1 match {\ncase 1 =>\n${|}case 2 =>\ncase 3 =>\n}" movedUpIs "1 match {\n  case 2 =>\n  case 1 =>\ncase 3 =>\n}"
    s"1 match {\ncase 1 =>\n${|}case 2 =>\ncase 3 =>\n}" movedDownIs "1 match {\ncase 1 =>\ncase 3 =>\ncase 2 =>\n}"

    s"1 match {\ncase 1 =>\ncase 2 =>\n${|}case 3 =>\n}" movedUpIs "1 match {\ncase 1 =>\ncase 3 =>\ncase 2 =>\n}"
    //    s"1 match {\ncase 1 =>\ncase 2 =>\n${|}case 3 =>\n}}" moveDownIsDisabled;
  }

  def testMultilineCaseClause(): Unit = {
    s"1 switch {\n${|}case 1 =>{\n//clause 1\n}\n\ncase 2 =>\n}}" movedDownIs "1 switch {\ncase 2 =>\n\ncase 1 =>{\n  //clause 1\n}\n}}"
    s"1 switch {\n${|}case 1 =>\n\ncase 2 =>{\n//clause 2\n}\n}}" movedDownIs "1 switch {\ncase 2 =>{\n//clause 2\n}\n\ncase 1 =>\n}}"
  }

  def testMoveOverImportStatement(): Unit = {
    s"import foo.bar\n${|}def baz = { 1\n2\n }" movedUpIs "def baz = { 1\n  2\n}\nimport foo.bar\n"
    s"${|}def baz = { 1\n2\n }\nimport foo.bar" movedDownIs "import foo.bar\ndef baz = { 1\n  2\n}\n"

    //    s"${|}import foo.bar\ndef baz = { 1\n2\n }" movedDownIs "def baz = { 1\n2\n }\nimport foo.bar"
    //    s"def baz = { 1\n2\n }\n${|}import foo.bar" movedUpIs "import foo.bar\ndef baz = { 1\n2\n }"
  }

  def testIfStatement(): Unit = {
    s"${|}if (true) {\nfoo\n}\nprintln()" movedDownIs "println()\nif (true) {\n  foo\n}\n"

    s"${|}foo\nif (false) {\nbar\n}".moveDownIsDisabled()
  }

  def testForStatement(): Unit = {
    s"${|}for (x <- xs) {\nfoo\n}\nprintln()" movedDownIs "println()\nfor (x <- xs) {\n  foo\n}\n"

    s"${|}foo\nfor (x <- xs) {\nbar\n}".moveDownIsDisabled()
  }

  def testMatchStatement(): Unit = {
    s"${|}1 match {\n  case 1 => null\n}\nprintln()" movedDownIs "println()\n1 match {\n  case 1 => null\n}\n"
  }

  def testTryStatement(): Unit = {
    s"${|}try {\n  foo\n} catch {\n  case _ =>\n}\nprintln()" movedDownIs "println()\ntry {\n  foo\n} catch {\n  case _ =>\n}\n"
  }

  def testMethodCallWithBlockExpression(): Unit = {
    //    s"${|}foo()\nbar" moveDownIsDisabled;
    //    s"${|}foo() {}\nbar" moveDownIsDisabled;

    s"${|}foo() {\nfoo\n}\nbar" movedDownIs "bar\nfoo() {\n  foo\n}\n"
  }

  def testClass(): Unit = {
    val before =
      s"""class A {
         |  println()
         |}
         |
         |class B $CARET{
         |  println()
         |}
         |
         |class C {
         |  println()
         |}
         |""".stripMargin
    val after =
      s"""class B $CARET{
         |  println()
         |}
         |
         |class A {
         |  println()
         |}
         |
         |class C {
         |  println()
         |}
         |""".stripMargin
    before movedUpIs after
  }

  def testClass_WithParameters(): Unit = {
    val before =
      s"""class A {
         |  println()
         |}
         |
         |class B(x: Int)(y: Int) $CARET{
         |  println()
         |}
         |
         |class C {
         |  println()
         |}
         |""".stripMargin
    val after =
      s"""class B(x: Int)(y: Int) $CARET{
         |  println()
         |}
         |
         |class A {
         |  println()
         |}
         |
         |class C {
         |  println()
         |}
         |""".stripMargin
    before movedUpIs after
  }

  def testClass_WithMultilineParameters(): Unit = {
    val before =
      s"""class A {
         |  println()
         |}
         |
         |class B($CARET
         |  x: Int,
         |  y: Int
         |) {
         |  println()
         |}
         |
         |class C {
         |  println()
         |}
         |""".stripMargin
    val after =
      s"""class B($CARET
         |  x: Int,
         |  y: Int
         |) {
         |  println()
         |}
         |
         |class A {
         |  println()
         |}
         |
         |class C {
         |  println()
         |}
         |""".stripMargin
    before movedUpIs after
  }

  def testClass_WithExtendsList(): Unit = {
    val before =
      s"""class A {
         |  println()
         |}
         |
         |class B $CARET extends Base with T1 with T2 {
         |  println()
         |}
         |
         |class C {
         |  println()
         |}
         |""".stripMargin
    val after =
      s"""class B $CARET extends Base with T1 with T2 {
         |  println()
         |}
         |
         |class A {
         |  println()
         |}
         |
         |class C {
         |  println()
         |}
         |""".stripMargin
    before movedUpIs after
  }

  def testClass_WithMultilineExtendsList(): Unit = {
    val before =
      s"""class A {
         |  println()
         |}
         |
         |class B$CARET
         |  extends Base
         |    with T1
         |    with T2 {
         |  println()
         |}
         |
         |class C {
         |  println()
         |}
         |""".stripMargin
    val after =
      s"""class B$CARET
         |  extends Base
         |    with T1
         |    with T2 {
         |  println()
         |}
         |
         |class A {
         |  println()
         |}
         |
         |class C {
         |  println()
         |}
         |""".stripMargin
    before movedUpIs after
  }

  def testClass_WithoutBody(): Unit = {
    val before =
      s"""class A {
         |  println()
         |}
         |
         |${CARET}class B(x: Int)(y: Int)
         |
         |class C {
         |  println()
         |}
         |""".stripMargin
    val after =
      s"""${CARET}class B(x: Int)(y: Int)
         |
         |class A {
         |  println()
         |}
         |
         |class C {
         |  println()
         |}
         |""".stripMargin
    before movedUpIs after
  }

  def testClass_WithAttachedLineComment(): Unit = {
    val before =
      s"""class A
         |
         |//line comment 1
         |//line comment 2
         |class B $CARET{
         |  println()
         |}
         |
         |class C
         |""".stripMargin
    val after =
      s"""//line comment 1
         |//line comment 2
         |class B $CARET{
         |  println()
         |}
         |
         |class A
         |
         |class C
         |""".stripMargin
    before movedUpIs after
  }

  def testClass_WithAttachedBlockComment(): Unit = {
    val before =
      s"""class A
         |
         |/*
         | * block
         | * comment
         | */
         |class B $CARET{
         |  println()
         |}
         |
         |class C
         |""".stripMargin
    val after =
      s"""/*
         | * block
         | * comment
         | */
         |class B $CARET{
         |  println()
         |}
         |
         |class A
         |
         |class C
         |""".stripMargin
    before movedUpIs after
  }

  def testClass_WithAttachedDocComment(): Unit = {
    val before =
      s"""class A
         |
         |/**
         | * doc
         | * comment
         | */
         |class B $CARET{
         |  println()
         |}
         |
         |class C
         |""".stripMargin
    val after =
      s"""/**
         | * doc
         | * comment
         | */
         |class B $CARET{
         |  println()
         |}
         |
         |class A
         |
         |class C
         |""".stripMargin
    before movedUpIs after
  }

  def testDef_WithMultilineParameters(): Unit = {
    val before =
      s"""class A {
         |  println()
         |}
         |
         |def foo1($CARET
         |  x: Int,
         |  y: Int
         |): Unit = {
         |  println()
         |}
         |
         |class C {
         |  println()
         |}
         |""".stripMargin
    val after =
      s"""def foo1($CARET
         |  x: Int,
         |  y: Int
         |): Unit = {
         |  println()
         |}
         |
         |class A {
         |  println()
         |}
         |
         |class C {
         |  println()
         |}
         |""".stripMargin
    before movedUpIs after
  }

  def testAnnotationWithVal(): Unit = {
    val before =
      s"""@deprecated("test val")
         |${|}val myVal = {
         |  42
         |}
         |
         |class Other
         |""".stripMargin
    val after =
      s"""class Other
         |
         |@deprecated("test val")
         |${|}val myVal = {
         |  42
         |}
         |""".stripMargin
    before movedDownIs after
  }

  def testAnnotationWithVal_WithAnotherVal(): Unit = {
    val before =
      s"""@deprecated("test val")
         |${|}val myVal = {
         |  42
         |}
         |
         |val other =  {
         |  23
         |}
         |""".stripMargin
    val after =
      s"""val other =  {
         |  23
         |}
         |
         |@deprecated("test val")
         |${|}val myVal = {
         |  42
         |}
         |""".stripMargin
    before movedDownIs after
  }

  def testAnnotationWithDef(): Unit = {
    val before =
      s"""@deprecated("test val")
         |${|}def myDef = ???
         |
         |class Other
         |""".stripMargin
    val after =
      s"""class Other
         |
         |@deprecated("test val")
         |${|}def myDef = ???
         |""".stripMargin
    before movedDownIs after
  }

  def testAnnotationWithClass(): Unit = {
    val before =
      s"""@deprecated("test val")
         |${|}class MyClass
         |
         |class Other
         |""".stripMargin
    val after =
      s"""class Other
         |
         |@deprecated("test val")
         |${|}class MyClass
         |""".stripMargin
    before movedDownIs after
  }

  def testMultipleValBindings(): Unit = {
    val before =
      s"""val (
         |  ${|}x,
         |  y,
         |  z
         |) = (1, 2, 3)
         |
         |"after"
         |""".stripMargin
    val after =
      s"""val (
         |  y,
         |  ${|}x,
         |  z
         |) = (1, 2, 3)
         |
         |"after"
         |""".stripMargin
    before movedDownIs after
  }
}



package org.jetbrains.plugins.scala
package lang.refactoring

class ScalaStatementMoverTest extends StatementMoverTestBase {
  def testSingleLineMember(): Unit = {
    "|def a".moveUpIsDisabled()
    "|def a".moveDownIsDisabled()

    "|def a\ndef b\n".moveUpIsDisabled()
    "def a\n|def b\n" movedUpIs "def b\ndef a"

    "|def a\ndef b" movedDownIs "def b\ndef a"
    "def a\n|def b".moveDownIsDisabled()

    "|def a\ndef b\ndef c".moveUpIsDisabled()
    "def a\n|def b\ndef c" movedUpIs "def b\ndef a\ndef c"
    "def a\ndef b\n|def c" movedUpIs "def a\ndef c\ndef b"

    "|def a\ndef b\ndef c" movedDownIs "def b\ndef a\ndef c"
    "def a\n|def b\ndef c" movedDownIs "def a\ndef c\ndef b"
    "def a\ndef b\n|def c".moveDownIsDisabled()
  }

  def testCursorPositioning(): Unit = {
    "|def a\ndef b" movedDownIs "def b\ndef a"
    "def| a\ndef b" movedDownIs "def b\ndef a"
    "def a|\ndef b" movedDownIs "def b\ndef a"
    "def a {|}\ndef b {}" movedDownIs "def b {}\ndef a {}"
    "def a {}|\ndef b {}" movedDownIs "def b {}\ndef a {}"
  }

  def testCursorLinePositioning(): Unit = {
    "def a {\n|\n}\ndef b {\n\n}".moveDownIsDisabled()
  }

  def testLineSpace(): Unit = {
    "def a\n\n|def b" movedUpIs "def b\n\ndef a"
    "|def a\n\ndef b" movedDownIs "def b\n\ndef a"
  }

  def testExpressionAsSource(): Unit = {
    "|v = 1\ndef a".moveDownIsDisabled()
  }

  def testExpressionAsTarget(): Unit = {
    "|def a\nv = 1\ndef b" movedDownIs "v = 1\ndef a\ndef b"
  }

  def testSkipComment(): Unit = {
    "|def a\n//comment\n\ndef b" movedDownIs "def b\n//comment\n\ndef a"
  }

  def testSourceComment(): Unit = {
    "//source\n|def a\ndef b" movedDownIs "def b\n//source\ndef a"
    "//source 1\n//source 2\n|def a\ndef b" movedDownIs "def b\n//source 1\n//source 2\ndef a"
    "//foo\n\n//source\n|def a\ndef b" movedDownIs "//foo\n\ndef b\n//source\ndef a"
  }

  def testDestinationComment(): Unit = {
    "//source\ndef a\n|def b" movedUpIs "def b\n//source\ndef a"
    "//source 1\n//source 2\ndef a\n|def b" movedUpIs "def b\n//source 1\n//source 2\ndef a"
    "//foo\n\n//source\ndef a\n|def b" movedUpIs "//foo\n\ndef b\n//source\ndef a"
  }

  def testMultipleLinesMember(): Unit = {
    "def a {\n// method a\n}\n\n|def b {\n// method b\n}" movedUpIs "def b {\n// method b\n}\n\ndef a {\n// method a\n}"
    "|def a {\n// method a\n}\n\ndef b {\n// method b\n}" movedDownIs "def b {\n// method b\n}\n\ndef a {\n// method a\n}"

    "|def a\n\ndef b {\n// method b\n}" movedDownIs "def b {\n// method b\n}\n\ndef a"
    "|def a {\n// method a\n}\n\ndef b" movedDownIs "def b\n\ndef a {\n// method a\n}"
  }

  def testCaseClause(): Unit = {
    //    "1 match {\n|case 1 =>\ncase 2 =>\ncase 3 =>\n}}" moveUpIsDisabled;
    "1 match {\n|case 1 =>\ncase 2 =>\ncase 3 =>\n}}" movedDownIs "1 match {\ncase 2 =>\ncase 1 =>\ncase 3 =>\n}}"

    "1 match {\ncase 1 =>\n|case 2 =>\ncase 3 =>\n}}" movedUpIs "1 match {\ncase 2 =>\ncase 1 =>\ncase 3 =>\n}}"
    "1 match {\ncase 1 =>\n|case 2 =>\ncase 3 =>\n}}" movedDownIs "1 match {\ncase 1 =>\ncase 3 =>\ncase 2 =>\n}}"

    "1 match {\ncase 1 =>\ncase 2 =>\n|case 3 =>\n}}" movedUpIs "1 match {\ncase 1 =>\ncase 3 =>\ncase 2 =>\n}}"
    //    "1 match {\ncase 1 =>\ncase 2 =>\n|case 3 =>\n}}" moveDownIsDisabled;
  }

  def testMultilineCaseClause(): Unit = {
    "1 switch {\n|case 1 =>{\n//clause 1\n}\n\ncase 2 =>\n}}" movedDownIs "1 switch {\ncase 2 =>\n\ncase 1 =>{\n//clause 1\n}\n}}"
    "1 switch {\n|case 1 =>\n\ncase 2 =>{\n//clause 2\n}\n}}" movedDownIs "1 switch {\ncase 2 =>{\n//clause 2\n}\n\ncase 1 =>\n}}"
  }

  def testMoveOverImportStatement(): Unit = {
    "import foo.bar\n|def baz = { 1\n2\n }" movedUpIs "def baz = { 1\n2\n }\nimport foo.bar"
    "|def baz = { 1\n2\n }\nimport foo.bar" movedDownIs "import foo.bar\ndef baz = { 1\n2\n }"

    //    "|import foo.bar\ndef baz = { 1\n2\n }" movedDownIs "def baz = { 1\n2\n }\nimport foo.bar"
    //    "def baz = { 1\n2\n }\n|import foo.bar" movedUpIs "import foo.bar\ndef baz = { 1\n2\n }"
  }

  def testIfStatement(): Unit = {
    "|if (true) {\nfoo\n}\nprintln()" movedDownIs "println()\nif (true) {\nfoo\n}"

    "|foo\nif (false) {\nbar\n}".moveDownIsDisabled()
  }

  def testForStatement(): Unit = {
    "|for (x <- xs) {\nfoo\n}\nprintln()" movedDownIs "println()\nfor (x <- xs) {\nfoo\n}"

    "|foo\nfor (x <- xs) {\nbar\n}".moveDownIsDisabled()
  }

  def testMatchStatement(): Unit = {
    "|1 match {\n  case 1 => null\n}\nprintln()" movedDownIs "println()\n1 match {\n  case 1 => null\n}"
  }

  def testTryStatement(): Unit = {
    "|try {\n  foo\n} catch {\n  case _ =>\n}\nprintln()" movedDownIs "println()\ntry {\n  foo\n} catch {\n  case _ =>\n}"
  }

  def testMethodCallWithBlockExpression(): Unit = {
    //    "|foo()\nbar" moveDownIsDisabled;
    //    "|foo() {}\nbar" moveDownIsDisabled;

    "|foo() {\nfoo\n}\nbar" movedDownIs "bar\nfoo() {\nfoo\n}"
  }
}

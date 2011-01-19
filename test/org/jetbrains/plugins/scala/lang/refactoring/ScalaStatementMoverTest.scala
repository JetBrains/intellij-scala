package org.jetbrains.plugins.scala.lang.refactoring

/**
 * Pavel Fatin
 */

class ScalaStatementMoverTest extends StatementMoverTestBase {
  def testSingleLineMember() {
    "|def a" moveUpIsDisabled;
    "|def a" moveDownIsDisabled

    "|def a\ndef b\n" moveUpIsDisabled;
    "def a\n|def b\n" movedUpIs "def b\ndef a"

    "|def a\ndef b" movedDownIs "def b\ndef a";
    "def a\n|def b" moveDownIsDisabled

    "|def a\ndef b\ndef c" moveUpIsDisabled;
    "def a\n|def b\ndef c" movedUpIs "def b\ndef a\ndef c"
    "def a\ndef b\n|def c" movedUpIs "def a\ndef c\ndef b"

    "|def a\ndef b\ndef c" movedDownIs "def b\ndef a\ndef c"
    "def a\n|def b\ndef c" movedDownIs "def a\ndef c\ndef b"
    "def a\ndef b\n|def c" moveDownIsDisabled
  }

  def testCursorPositioning {
    "|def a\ndef b" movedDownIs "def b\ndef a";
    "def| a\ndef b" movedDownIs "def b\ndef a";
    "def a|\ndef b" movedDownIs "def b\ndef a";
    "def a {|}\ndef b {}" movedDownIs "def b {}\ndef a {}";
    "def a {}|\ndef b {}" movedDownIs "def b {}\ndef a {}";
  }

  def testCursorLinePositioning {
    "def a {\n|\n}\ndef b {\n\n}" moveDownIsDisabled
  }

  def testLineSpace() {
    "def a\n\n|def b" movedUpIs "def b\n\ndef a"
    "|def a\n\ndef b" movedDownIs "def b\n\ndef a"
  }

  def testOtherStatement {
    "|def a\nv = 1\ndef b" moveDownIsDisabled;
  }

  def testSkipComment {
    "|def a\n//comment\n\ndef b" movedDownIs "def b\n//comment\n\ndef a";
  }

  def testSourceComment {
    "//source\n|def a\ndef b" movedDownIs "def b\n//source\ndef a";
    "//source 1\n//source 2\n|def a\ndef b" movedDownIs "def b\n//source 1\n//source 2\ndef a";
    "//foo\n\n//source\n|def a\ndef b" movedDownIs "//foo\n\ndef b\n//source\ndef a";
  }

  def testDestinationComment {
    "//source\ndef a\n|def b" movedUpIs "def b\n//source\ndef a";
    "//source 1\n//source 2\ndef a\n|def b" movedUpIs "def b\n//source 1\n//source 2\ndef a";
    "//foo\n\n//source\ndef a\n|def b" movedUpIs "//foo\n\ndef b\n//source\ndef a";
  }

  def testMultipleLinesMember() {
    "def a {\n// method a\n}\n\n|def b {\n// method b\n}" movedUpIs "def b {\n// method b\n}\n\ndef a {\n// method a\n}"
    "|def a {\n// method a\n}\n\ndef b {\n// method b\n}" movedDownIs "def b {\n// method b\n}\n\ndef a {\n// method a\n}"

    "|def a\n\ndef b {\n// method b\n}" movedDownIs "def b {\n// method b\n}\n\ndef a"
    "|def a {\n// method a\n}\n\ndef b" movedDownIs "def b\n\ndef a {\n// method a\n}"
  }

  def testCaseClause {
    "1 switch {\n|case 1 =>\ncase 2 =>\ncase 3 =>\n}}" moveUpIsDisabled;
    "1 switch {\n|case 1 =>\ncase 2 =>\ncase 3 =>\n}}" movedDownIs "1 switch {\ncase 2 =>\ncase 1 =>\ncase 3 =>\n}}";

    "1 switch {\ncase 1 =>\n|case 2 =>\ncase 3 =>\n}}" movedUpIs "1 switch {\ncase 2 =>\ncase 1 =>\ncase 3 =>\n}}";
    "1 switch {\ncase 1 =>\n|case 2 =>\ncase 3 =>\n}}" movedDownIs "1 switch {\ncase 1 =>\ncase 3 =>\ncase 2 =>\n}}";

    "1 switch {\ncase 1 =>\ncase 2 =>\n|case 3 =>\n}}" movedUpIs "1 switch {\ncase 1 =>\ncase 3 =>\ncase 2 =>\n}}";
    "1 switch {\ncase 1 =>\ncase 2 =>\n|case 3 =>\n}}" moveDownIsDisabled;
  }

  def testMultilineCaseClause {
    "1 switch {\n|case 1 =>{\n//clause 1\n}\n\ncase 2 =>\n}}" movedDownIs "1 switch {\ncase 2 =>\n\ncase 1 =>{\n//clause 1\n}\n}}";
    "1 switch {\n|case 1 =>\n\ncase 2 =>{\n//clause 2\n}\n}}" movedDownIs "1 switch {\ncase 2 =>{\n//clause 2\n}\n\ncase 1 =>\n}}";
  }
}

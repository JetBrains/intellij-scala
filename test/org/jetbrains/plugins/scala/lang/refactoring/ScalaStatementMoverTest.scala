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

  def testLineSpace() {
    "def a\n\n|def b" movedUpIs "def b\n\ndef a"
    "|def a\n\ndef b" movedDownIs "def b\n\ndef a"
  }

  def testMultipleLinesMember() {
    "def a {\n// method a\n}\n\n|def b {\n// method b\n}" movedUpIs "def b {\n// method b\n}\n\ndef a {\n// method a\n}"
    "|def a {\n// method a\n}\n\ndef b {\n// method b\n}" movedDownIs "def b {\n// method b\n}\n\ndef a {\n// method a\n}"
  }

}

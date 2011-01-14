package org.jetbrains.plugins.scala.lang.refactoring

import junit.framework.Assert._

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
}

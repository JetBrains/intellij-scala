package org.jetbrains.plugins.scala.lang.refactoring

import junit.framework.Assert._

/**
 * Pavel Fatin
 */

class ScalaStatementMoverTest extends StatementMoverTestBase {
  def testSingleLineMember() {
    assertEquals(None, move("|def a", Up))
    assertEquals(None, move("|def a", Down))

    assertEquals(None, move("|def a\ndef b\n", Up))
    assertEquals(Some("def b\ndef a"), move("def a\n|def b\n", Up))

    assertEquals(Some("def b\ndef a"), move("|def a\ndef b", Down))
    assertEquals(None, move("def a\n|def b", Down))

    assertEquals(None, move("def a|\ndef b\ndef c", Up))
    assertEquals(Some("def b\ndef a\ndef c"), move("def a\ndef b|\ndef c", Up))
    assertEquals(Some("def a\ndef c\ndef b"), move("def a\ndef b\n|def c", Up))

    assertEquals(Some("def b\ndef a\ndef c"), move("|def a\ndef b\ndef c", Down))
    assertEquals(Some("def a\ndef c\ndef b"), move("def a\n|def b\ndef c", Down))
    assertEquals(None, move("def a\ndef b\n|def c", Down))
  }
}

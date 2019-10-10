package org.jetbrains.plugins.scala.extensions

import junit.framework.TestCase
import org.junit.Assert._

class IteratorExtTest extends TestCase {

  def testLastOption_EmptyIterator(): Unit =
    assertEquals(None, Iterator().lastOption)

  def testLastOption_IteratorWithOneElement(): Unit =
    assertEquals(Some("1"), Iterator("1").lastOption)

  def testLastOption_IteratorWithOnePrimitiveElement(): Unit =
    assertEquals(Some(1), Iterator(1).lastOption)

  def testLastOption_IteratorWithOneNullElement(): Unit =
    assertEquals(Some(null), Iterator(null).lastOption)

  def testLastOption_IteratorWithManyElements(): Unit =
    assertEquals(Some("3"), Iterator("1", "2", "3").lastOption)
}
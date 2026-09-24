package org.jetbrains.plugins.scala.lang.optimize

import junit.framework.TestCase
import org.jetbrains.plugins.scala.editor.importOptimizer.BufferUpdate
import org.junit.Assert

import scala.collection.mutable.ArrayBuffer

class IncrementalBufferUpdateTest extends TestCase {

  private def doTest(from: Array[String], finalResult: Array[String]): Unit = {
    val buffer = ArrayBuffer(from.toSeq*)
    BufferUpdate.updateIncrementally(buffer, finalResult)(identity)

    Assert.assertEquals(buffer, finalResult.toSeq)
  }

  def testNoChange(): Unit = {
    doTest(
      Array("1", "2", "3", "4", "5"),
      Array("1", "2", "3", "4", "5")
    )

    doTest(
      Array(),
      Array()
    )

    doTest(
      Array("A"),
      Array("A")
    )
  }

  def testRemoveSome(): Unit = {
    doTest(
      Array("1", "2", "3", "4", "5"),
      Array("1", "3", "5")
    )

    doTest(
      Array("1", "2", "3", "4", "5"),
      Array("1", "2", "3", "4")
    )

    doTest(
      Array("1", "2", "3", "4", "5"),
      Array("2", "3", "4", "5")
    )

    doTest(
      Array("1", "2", "3", "4", "5"),
      Array("1", "5")
    )
  }

  def testAddSome(): Unit = {
    doTest(
      Array("1", "3", "5"),
      Array("1", "2", "3", "4", "5")
    )

    doTest(
      Array("1", "2", "3", "4"),
      Array("1", "2", "3", "4", "5")
    )

    doTest(
      Array("2", "3", "4", "5"),
      Array("1", "2", "3", "4", "5")
    )

    doTest(
      Array("1", "5"),
      Array("1", "2", "3", "4", "5")
    )
  }

  def testReplaceEverything(): Unit = {
    doTest(
      Array("1", "2", "3", "4", "5"),
      Array("A", "B", "C", "D", "E")
    )
    doTest(
      Array("1"),
      Array("A", "B", "C", "D", "E")
    )
    doTest(
      Array("1", "2", "3", "4", "5"),
      Array("A")
    )
  }

  def testSomeChanges(): Unit = {
    doTest(
      Array("1", "2", "3", "4", "5"),
      Array("1", "2", "A", "B", "C", "4", "D", "E")
    )
    doTest(
      Array("1", "2", "A", "B", "C", "4", "D", "E"),
      Array("1", "2", "3", "4", "5")
    )
    doTest(
      Array("1", "2", "3", "4", "5"),
      Array("1", "3", "4", "A", "B", "5")
    )
    doTest(
      Array("1", "3", "4", "A", "B", "5"),
      Array("1", "2", "3", "4", "5")
    )

  }

}

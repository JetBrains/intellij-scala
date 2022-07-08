package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.util.AliasExports

class RangeToIndicesTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[RangeToIndicesInspection]

  override protected val hint: String =
    "Replace with seq.indices"

  def testRange(): Unit = if (!AliasExports.aliasExportsEnabled(getProject)) { // TODO SCL-19972
    doTest(
      s"val seq = Seq(1); ${START}Range(0, seq.size)$END",
      "val seq = Seq(1); Range(0, seq.size)",
      "val seq = Seq(1); seq.indices"
    )
  }

  def testUntil(): Unit = {
    doTest(
      s"val seq = Seq(1); ${START}0 until seq.size$END",
      "val seq = Seq(1); 0 until seq.size",
      "val seq = Seq(1); seq.indices"
    )
  }

  def testUntil2(): Unit = {
    doTest(
      s"val seq = Seq(1); ${START}0.until(seq.size)$END",
      "val seq = Seq(1); 0.until(seq.size)",
      "val seq = Seq(1); seq.indices"
    )
  }

  def testTo(): Unit = {
    doTest(
      s"val seq = Seq(1); ${START}0 to (seq.length - 1)$END",
      "val seq = Seq(1); 0 to (seq.length - 1)",
      "val seq = Seq(1); seq.indices"
    )
  }

  def testArray(): Unit = if (!AliasExports.aliasExportsEnabled(getProject)) { // TODO SCL-19972
    doTest(
      s"val seq = Array(1); ${START}Range(0, seq.length)$END",
      "val seq = Array(1); Range(0, seq.length)",
      "val seq = Array(1); seq.indices"
    )
  }

  def testSet(): Unit = {
    checkTextHasNoErrors("val seq = Set(1); Range(0, seq.size)")
  }
}

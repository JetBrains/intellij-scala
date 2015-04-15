package org.jetbrains.plugins.scala.codeInspection.collections

/**
 * @author Nikolay.Tropin
 */
class RangeToIndicesTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[RangeToIndicesInspection]

  override def hint: String = "Replace with seq.indices"

  def testRange() = {
    doTest(
      s"val seq = Seq(1); ${START}Range(0, seq.size)$END",
      "val seq = Seq(1); Range(0, seq.size)",
      "val seq = Seq(1); seq.indices"
    )
  }

  def testUntil() = {
    doTest(
      s"val seq = Seq(1); ${START}0 until seq.size$END",
      "val seq = Seq(1); 0 until seq.size",
      "val seq = Seq(1); seq.indices"
    )
  }

  def testUntil2() = {
    doTest(
      s"val seq = Seq(1); ${START}0.until(seq.size)$END",
      "val seq = Seq(1); 0.until(seq.size)",
      "val seq = Seq(1); seq.indices"
    )
  }

  def testTo() = {
    doTest(
      s"val seq = Seq(1); ${START}0 to (seq.length - 1)$END",
      "val seq = Seq(1); 0 to (seq.length - 1)",
      "val seq = Seq(1); seq.indices"
    )
  }

  def testArray() = {
    doTest(
      s"val seq = Array(1); ${START}Range(0, seq.length)$END",
      "val seq = Array(1); Range(0, seq.length)",
      "val seq = Array(1); seq.indices"
    )
  }

  def testSet() = {
    checkTextHasNoErrors("val seq = Set(1); Range(0, seq.size)")
  }
}

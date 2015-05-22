package org.jetbrains.plugins.scala.codeInspection.collections

/**
 * @author Nikolay.Tropin
 */
class ComparingDiffCollectionKindsTest extends OperationsOnCollectionInspectionTest {
  import org.jetbrains.plugins.scala.codeInspection.collections.ComparingDiffCollectionKinds.convertHint
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] =
    classOf[ComparingDiffCollectionKindsInspection]

  override def hint: String = ComparingDiffCollectionKinds.hint

  def testSeqSet(): Unit = {
    val leftHint = convertHint("left", "Set")
    val rightHint = convertHint("right", "Seq")
    val selection = s"Seq(1) $START==$END Set(1)"
    val text = "Seq(1) == Set(1)"
    check(selection, leftHint)
    check(selection, rightHint)
    testFix(text, "Seq(1).toSet == Set(1)", leftHint)
    testFix(text, "Seq(1) == Set(1).toSeq", rightHint)
  }

  def testSeqIterator(): Unit = {
    val leftHint = convertHint("left", "Iterator")
    val rightHint = convertHint("right", "Seq")
    val selection = s"Seq(1) ++ Seq(2) $START==$END Iterator(1)"
    val text = "Seq(1) ++ Seq(2) == Iterator(1)"
    check(selection, leftHint)
    check(selection, rightHint)
    testFix(text, "(Seq(1) ++ Seq(2)).toIterator == Iterator(1)", leftHint)
    testFix(text, "Seq(1) ++ Seq(2) == Iterator(1).toSeq", rightHint)
  }

  def testSeqMap(): Unit = {
    val leftHint = convertHint("left", "Map")
    val rightHint = convertHint("right", "Seq")
    val selection = s"Seq((1, 2)) $START!=$END Map(1 -> 2)"
    val text = "Seq((1, 2)) != Map(1 -> 2)"
    check(selection, leftHint)
    check(selection, rightHint)
    testFix(text, "Seq((1, 2)).toMap != Map(1 -> 2)", leftHint)
    testFix(text, "Seq((1, 2)) != Map(1 -> 2).toSeq", rightHint)
  }

  def testSeqArray(): Unit = {
    val leftHint = convertHint("left", "Array")
    val rightHint = convertHint("right", "Seq")
    val selection = s"Seq(1).${START}equals$END(Array(1))"
    val text = "Seq(1).equals(Array(1))"
    checkTextHasNoErrors(selection, leftHint, inspectionClass)
    check(selection, rightHint)
    testFix(text, "Seq(1).equals(Array(1).toSeq)", rightHint)
  }

  def testSeqSeq(): Unit = {
    val leftHint = convertHint("left", "Seq")
    val rightHint = convertHint("right", "Seq")
    checkTextHasNoErrors("Seq(1) == Seq(1)", leftHint, inspectionClass)
    checkTextHasNoErrors("Seq(1) == Seq(1)", rightHint, inspectionClass)
  }

  def testNullAndNothing(): Unit = {
    checkTextHasNoErrors("Set(1) == null")
    checkTextHasNoErrors("Map(1 -> 2) == ???")
  }
}

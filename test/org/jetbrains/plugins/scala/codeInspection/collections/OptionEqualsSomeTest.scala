package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
 * @author Nikolay.Tropin
 */
class OptionEqualsSomeTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[OptionEqualsSomeToContainsInspection]

  override protected def libVersion: ScalaSdkVersion = ScalaSdkVersion._2_11

  override def hint: String = OptionEqualsSomeToContains.hint

  def test1(): Unit = {
    doTest(
      s"${START}Option(1) == Some(2)$END",
      "Option(1) == Some(2)",
      "Option(1).contains(2)"
    )
  }

  def test2(): Unit = {
    doTest(
      s"${START}Option(1) equals Some(2)$END",
      "Option(1) equals Some(2)",
      "Option(1).contains(2)"
    )
  }

  def test3(): Unit = {
    doTest(
      s"${START}Some(2) == Option(1)$END",
      "Some(2) == Option(1)",
      "Option(1).contains(2)"
    )
  }

  def test4(): Unit = {
    doTest(
      s"${START}Option(1).equals(Some(2))$END",
      "Option(1).equals(Some(2))",
      "Option(1).contains(2)"
    )
  }
}

class OptionNotEqualsSomeTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[OptionEqualsSomeToContainsInspection]

  override def hint: String = OptionNotEqualsSomeToNotContains.hint

  override protected def libVersion: ScalaSdkVersion = ScalaSdkVersion._2_11

  def test1(): Unit = {
    doTest(
      s"${START}Option(1) != Some(2)$END",
      "Option(1) != Some(2)",
      "!Option(1).contains(2)"
    )
  }

  def test2(): Unit = {
    doTest(
      s"${START}Some(2) != Option(1)$END",
      "Some(2) != Option(1)",
      "!Option(1).contains(2)"
    )
  }
}

class OptionEqualsSome_2_10_Test extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[OptionEqualsSomeToContainsInspection]

  override protected def libVersion: ScalaSdkVersion = ScalaSdkVersion._2_10

  override def hint: String = OptionEqualsSomeToContains.hint

  def testNoErrors(): Unit = {
    checkTextHasNoErrors("Option(1) == Some(2)")
  }
}

class OptionNotEqualsSome_2_10_Test extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[OptionEqualsSomeToContainsInspection]

  override def hint: String = OptionNotEqualsSomeToNotContains.hint

  override protected def libVersion: ScalaSdkVersion = ScalaSdkVersion._2_10

  def testNoErrors(): Unit = {
    checkTextHasNoErrors("Option(1) == Some(2)")
  }
}

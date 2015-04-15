package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * @author Nikolay.Tropin
 */
class FilterOtherContainsTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[FilterOtherContainsInspection]

  override def hint: String = InspectionBundle.message("replace.filter.with.intersect")

  def testFunExpr(): Unit = {
    doTest(
      s"val others = Set(1,2); Set().${START}filter(x => others.contains(x))$END",
      "val others = Set(1,2); Set().filter(x => others.contains(x))",
      "val others = Set(1,2); Set().intersect(others)"
    )
  }

  def testUnderscore(): Unit = {
    doTest(
      s"val others = Set(1,2); Set().${START}filter(others.contains(_))$END",
      "val others = Set(1,2); Set().filter(others.contains(_))",
      "val others = Set(1,2); Set().intersect(others)"
    )
  }

  def testUnderscore2(): Unit = {
    doTest(
      s"val others = Set(1,2); Set().${START}filter(others.contains _)$END",
      "val others = Set(1,2); Set().filter(others.contains(_))",
      "val others = Set(1,2); Set().intersect(others)"
    )
  }

  def testUnderscoreInfix(): Unit = {
    doTest(
      s"val others = Set(1,2); Set().${START}filter(others contains _)$END",
      "val others = Set(1,2); Set().filter(others contains _)",
      "val others = Set(1,2); Set().intersect(others)"
    )
  }

  def testMethodValue(): Unit = {
    doTest(
      s"val others = Set(1,2); Set().${START}filter(others.contains)$END",
      "val others = Set(1,2); Set().filter(others.contains)",
      "val others = Set(1,2); Set().intersect(others)"
    )
  }

  def testFilterNotNotContains(): Unit = {
    doTest(
      s"val others = Set(1,2); Set().${START}filterNot(x => !others.contains(x))$END",
      "val others = Set(1,2); Set().filterNot(x => !others.contains(x))",
      "val others = Set(1,2); Set().intersect(others)"
    )
  }

  def testNotASet1(): Unit = {
    checkTextHasNoErrors("val others = Set(1,2); Seq().filter(others.contains)")
  }

  def testNotASet2(): Unit = {
    checkTextHasNoErrors("val others = Seq(1,2); Set().filter(others.contains)")
  }

  def testInnerSetDependsOnElement(): Unit = {
    checkTextHasNoErrors("Set().filter(x => Set(1 - x, 2 - x).contains(x))")
  }
}

class FilterOtherNotContainsTest extends OperationsOnCollectionInspectionTest {
  override val inspectionClass: Class[_ <: OperationOnCollectionInspection] = classOf[FilterOtherContainsInspection]

  override def hint: String = InspectionBundle.message("replace.filter.with.diff")

  def testFunExpr(): Unit = {
    doTest(
      s"val others = Set(1,2); Set().${START}filter(x => !others.contains(x))$END",
      "val others = Set(1,2); Set().filter(x => !others.contains(x))",
      "val others = Set(1,2); Set().diff(others)"
    )
  }

  def testUnderscore(): Unit = {
    doTest(
      s"val others = Set(1,2); Set().${START}filterNot(others.contains(_))$END",
      "val others = Set(1,2); Set().filterNot(others.contains(_))",
      "val others = Set(1,2); Set().diff(others)"
    )
  }

  def testUnderscoreInfix(): Unit = {
    doTest(
      s"val others = Set(1,2); Set().${START}filterNot(others contains _)$END",
      "val others = Set(1,2); Set().filterNot(others contains _)",
      "val others = Set(1,2); Set().diff(others)"
    )
  }

  def testMethodValue(): Unit = {
    doTest(
      s"val others = Set(1,2); Set().${START}filterNot(others.contains)$END",
      "val others = Set(1,2); Set().filterNot(others.contains)",
      "val others = Set(1,2); Set().diff(others)"
    )
  }

  def testNotASet1(): Unit = {
    checkTextHasNoErrors("val others = Set(1,2); Seq().filterNot(others.contains)")
  }

  def testNotASet2(): Unit = {
    checkTextHasNoErrors("val others = Seq(1,2); Set().filter(x => !others.contains(x))")
  }

  def testInnerSetDependsOnElement(): Unit = {
    checkTextHasNoErrors("Set().filter(x => !Set(1 - x, 2 - x).contains(x))")
  }
}

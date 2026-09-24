package org.jetbrains.plugins.scala
package codeInspection
package collections

abstract class ExistsEqualsTestBase extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[? <: OperationOnCollectionInspection] =
    classOf[ExistsEqualsInspection]
}

abstract class ReplaceWithContainsTestBase extends ExistsEqualsTestBase {
  override protected val hint: String =
    ScalaInspectionBundle.message("exists.equals.hint")
}

class ReplaceWithContainsTest extends ReplaceWithContainsTestBase {

  def test_1(): Unit = {
    val selected = s"List(0).${START}exists(x => x == 1)$END"
    checkTextHasError(selected)
    val text = "List(0).exists(x => x == 1)"
    val result = "List(0).contains(1)"
    testQuickFix(text, result, hint)
  }

  def test_2(): Unit = {
    val selected = s"List(0).${START}exists(_ == 1)$END"
    checkTextHasError(selected)
    val text = "List(0).exists(_ == 1)"
    val result = "List(0).contains(1)"
    testQuickFix(text, result, hint)
  }

  def test_3(): Unit = {
    val selected = s"List(0) ${START}exists (x => x == 1)$END"
    checkTextHasError(selected)
    val text = "List(0) exists (x => x == 1)"
    val result = "List(0) contains 1"
    testQuickFix(text, result, hint)
  }

  def test_4(): Unit = {
    val selected = s"List(0).${START}exists(1 == _)$END"
    checkTextHasError(selected)
    val text = "List(0).exists(1 == _)"
    val result = "List(0).contains(1)"
    testQuickFix(text, result, hint)
  }

  def test_5(): Unit = {
    val text = "List(0).exists(x => x == - x)"
    checkTextHasNoErrors(text)
  }

  def test_7(): Unit = {
    val text = "Map(1 -> \"1\").exists(_ == (1, \"1\"))"
    checkTextHasNoErrors(text)
  }
}

class ReplaceWithContainsTest_with_OptionContains extends ReplaceWithContainsTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_11

  def test_6(): Unit = {
    val selected = s"Some(0).${START}exists(_ == 1)$END"
    checkTextHasError(selected)
    val text = "Some(0).exists(_ == 1)"
    val result = "Some(0).contains(1)"
    testQuickFix(text, result, hint)
  }
}

class ReplaceWithContainsTest_without_OptionContains extends ReplaceWithContainsTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_10

  def test_6(): Unit = {
    val text = "Some(1).exists(_ == 1)"
    checkTextHasNoErrors(text)
  }
}


class ReplaceWithNotContainsTest extends ExistsEqualsTestBase {

  override protected val hint: String =
    ScalaInspectionBundle.message("forall.notEquals.hint")

  def testForallNotEquals(): Unit = {
    val selected = s"Seq(1, 2).${START}forall(_ != 2)$END"
    checkTextHasError(selected)
    val text = "Seq(1, 2).forall(_ != 2)"
    val result = "!Seq(1, 2).contains(2)"
    testQuickFix(text, result, hint)
  }
}

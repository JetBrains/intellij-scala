package org.jetbrains.plugins.scala
package codeInspection
package collections

import com.intellij.testFramework.EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

/**
 * @author Nikolay.Tropin
 */
abstract class OptionEqualsSomeToContainsInspectionTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[OptionEqualsSomeToContainsInspection]
}

class OptionEqualsSomeTest extends OptionEqualsSomeToContainsInspectionTest {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_11

  override protected val hint: String =
    OptionEqualsSomeToContains.hint

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

class OptionNotEqualsSomeTest extends OptionEqualsSomeToContainsInspectionTest {

  override protected val hint: String =
    OptionNotEqualsSomeToNotContains.hint

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

class OptionEqualsSome_2_10_Test extends OptionEqualsSomeToContainsInspectionTest {

  override protected val hint: String =
    OptionEqualsSomeToContains.hint

  override protected def supportedIn(version: ScalaVersion): Boolean = version <= Scala_2_10

  def testNoErrors(): Unit = {
    checkTextHasNoErrors("Option(1) == Some(2)")
  }
}

class OptionNotEqualsSome_2_10_Test extends OptionEqualsSomeToContainsInspectionTest {

  override protected val hint: String =
    OptionNotEqualsSomeToNotContains.hint

  override protected def supportedIn(version: ScalaVersion): Boolean = version <= Scala_2_10

  def testNoErrors(): Unit = {
    checkTextHasNoErrors("Option(1) == Some(2)")
  }
}

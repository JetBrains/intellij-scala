package org.jetbrains.plugins.scala
package codeInspection
package collections

abstract class RedundantHeadOrLastOptionInspectionTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[RedundantHeadOrLastOptionInspection]
}

class RedundantHeadOptionTest extends RedundantHeadOrLastOptionInspectionTest {

  override protected val hint: String =
    RedundantHeadOption.hint

  def test1(): Unit = {
    doTest(
      s"Some(1).${START}headOption$END",
       "Some(1).headOption",
       "Some(1)"
    )
  }

  def test2(): Unit = {
    doTest(
      s"Option(1).${START}headOption$END",
      "Option(1).headOption",
      "Option(1)"
    )
  }

  def test3(): Unit = {
    doTest(
      s"None.${START}headOption$END",
      "None.headOption",
      "None"
    )
  }

  def test4(): Unit = {
    doTest(
      s"Seq(1).find(_ => true).${START}headOption$END",
      "Seq(1).find(_ => true).headOption",
      "Seq(1).find(_ => true)"
    )
  }

  def test5(): Unit = {
    doTest(
      s"Option(1)$START headOption$END",
      "Option(1) headOption",
      "Option(1)"
    )
  }
}

class RedundantLastOptionTest extends RedundantHeadOrLastOptionInspectionTest {

  override protected val hint: String =
    RedundantLastOption.hint

  def test1(): Unit = {
    doTest(
      s"Some(1).${START}lastOption$END",
      "Some(1).lastOption",
      "Some(1)"
    )
  }

  def test2(): Unit = {
    doTest(
      s"Option(1).${START}lastOption$END",
      "Option(1).lastOption",
      "Option(1)"
    )
  }

  def test3(): Unit = {
    doTest(
      s"None.${START}lastOption$END",
      "None.lastOption",
      "None"
    )
  }

  def test4(): Unit = {
    doTest(
      s"Seq(1).find(_ => true).${START}lastOption$END",
      "Seq(1).find(_ => true).lastOption",
      "Seq(1).find(_ => true)"
    )
  }

  def test5(): Unit = {
    doTest(
      s"Option(1)$START lastOption$END",
      "Option(1) lastOption",
      "Option(1)"
    )
  }
}
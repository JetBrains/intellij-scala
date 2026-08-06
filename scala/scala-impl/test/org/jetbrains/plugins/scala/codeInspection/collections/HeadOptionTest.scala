package org.jetbrains.plugins.scala
package codeInspection
package collections

abstract class HeadOrLastOptionTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[HeadOrLastOptionInspection]
}

class HeadOptionTest extends HeadOrLastOptionTest {

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.with.headOption")

  def test1(): Unit = {
    doTest(
      s"val seq = Seq(0); ${START}if (seq.size != 0) Some(seq.head) else None$END",
      "val seq = Seq(0); if (seq.size != 0) Some(seq.head) else None",
      "val seq = Seq(0); seq.headOption"
    )
  }

  def test2(): Unit = {
    doTest(
      s"val seq = Seq(0); ${START}if (seq.nonEmpty) Some(seq.head) else None$END",
      "val seq = Seq(0); if (seq.nonEmpty) Some(seq.head) else None",
      "val seq = Seq(0); seq.headOption"
    )
  }

  def test3(): Unit = {
    doTest(
      s"""val seq = Seq(0)
         |${START}if (seq.isEmpty)
         |  None
         |else
         |  Some(seq.head)$END""".stripMargin,
      """val seq = Seq(0)
        |if (seq.isEmpty)
        |  None
        |else
        |  Some(seq.head)""".stripMargin,
      """val seq = Seq(0)
        |seq.headOption""".stripMargin
    )
  }

  def test4(): Unit = {
    doTest(
      s"val seq = Seq(0); seq.${START}lift(0)$END",
      "val seq = Seq(0); seq.lift(0)",
      "val seq = Seq(0); seq.headOption"
    )
  }

  def test5(): Unit = doTest(
    s"val seq = Seq(0); ${START}if (seq.nonEmpty) seq.headOption else None$END",
    "val seq = Seq(0); if (seq.nonEmpty) seq.headOption else None",
    "val seq = Seq(0); seq.headOption"
  )

  def test6(): Unit = doTest(
    s"val seq = Seq(0); ${START}if (seq.isEmpty) None else seq.headOption$END",
    "val seq = Seq(0); if (seq.isEmpty) None else seq.headOption",
    "val seq = Seq(0); seq.headOption"
  )
}

class LastOptionTest extends HeadOrLastOptionTest {

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.with.lastOption")

  def test1(): Unit = {
    doTest(
      s"val seq = Seq(0); ${START}if (seq.size != 0) Some(seq.last) else None$END",
      "val seq = Seq(0); if (seq.size != 0) Some(seq.last) else None",
      "val seq = Seq(0); seq.lastOption"
    )
  }

  def test2(): Unit = {
    doTest(
      s"val seq = Seq(0); ${START}if (seq.nonEmpty) Some(seq.last) else None$END",
      "val seq = Seq(0); if (seq.nonEmpty) Some(seq.last) else None",
      "val seq = Seq(0); seq.lastOption"
    )
  }

  def test3(): Unit = {
    doTest(
      s"""val seq = Seq(0)
         |${START}if (seq.isEmpty)
                    |  None
                    |else
                    |  Some(seq.last)$END""".stripMargin,
      """val seq = Seq(0)
        |if (seq.isEmpty)
        |  None
        |else
        |  Some(seq.last)""".stripMargin,
      """val seq = Seq(0)
        |seq.lastOption""".stripMargin
    )
  }

  def test4(): Unit = {
    doTest(
      s"val seq = Seq(0); seq.${START}lift(seq.size - 1)$END",
      "val seq = Seq(0); seq.lift(seq.size - 1)",
      "val seq = Seq(0); seq.lastOption"
    )
  }

  def test5(): Unit = doTest(
    s"val seq = Seq(0); ${START}if (seq.isEmpty) None else seq.lastOption$END",
    "val seq = Seq(0); if (seq.isEmpty) None else seq.lastOption",
    "val seq = Seq(0); seq.lastOption"
  )

  def test6(): Unit = doTest(
    s"val seq = Seq(0); ${START}if (seq.nonEmpty) seq.lastOption else None$END",
    "val seq = Seq(0); if (seq.nonEmpty) seq.lastOption else None",
    "val seq = Seq(0); seq.lastOption"
  )
}

package org.jetbrains.plugins.scala
package codeInspection
package collections

class CollectHeadOptionTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[CollectHeadOptionInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.collect.headOption.with.collectFirst")

  def testSeq(): Unit = {
    doTest(
      s"Seq(1, 2, 3).${START}collect { case i if i < 3 => i * 2 }.headOption$END",
      "Seq(1, 2, 3).collect { case i if i < 3 => i * 2 }.headOption",
      "Seq(1, 2, 3).collectFirst { case i if i < 3 => i * 2 }"
    )
  }

  def testSet(): Unit = {
    doTest(
      s"Set(1, 2, 3).${START}collect { case i if i < 3 => i * 2 }.headOption$END",
      "Set(1, 2, 3).collect { case i if i < 3 => i * 2 }.headOption",
      "Set(1, 2, 3).collectFirst { case i if i < 3 => i * 2 }"
    )
  }

  def testMap(): Unit = {
    doTest(
      s"""Map(1 -> "A", 2 -> "B", 3 -> "C").${START}collect { case (i, s) if i < 3 => s }.headOption$END""",
      """Map(1 -> "A", 2 -> "B", 3 -> "C").collect { case (i, s) if i < 3 => s }.headOption""",
      """Map(1 -> "A", 2 -> "B", 3 -> "C").collectFirst { case (i, s) if i < 3 => s }"""
    )
  }

}

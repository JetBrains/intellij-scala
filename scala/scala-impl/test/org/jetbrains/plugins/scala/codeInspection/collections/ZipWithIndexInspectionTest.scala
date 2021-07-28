package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle

class ZipWithIndexInspectionTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[ZipWithIndexInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("replace.with.zipWithIndex")

  def testNormal(): Unit = {
    doTest(
      s"""
         |val seq = Seq(1, 2); seq.${START}zip(seq.indices)$END
       """.stripMargin,
      """
        |val seq = Seq(1, 2); seq.zip(seq.indices)
      """.stripMargin,
      """
        |val seq = Seq(1, 2); seq.zipWithIndex
      """.stripMargin)
  }
}

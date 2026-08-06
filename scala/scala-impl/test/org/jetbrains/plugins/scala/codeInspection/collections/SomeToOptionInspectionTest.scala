package org.jetbrains.plugins.scala
package codeInspection
package collections

class SomeToOptionInspectionTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[SomeToOptionInspection]

  override protected val hint: String = ScalaInspectionBundle.message("replace.with.option")

  def testVal(): Unit = {
    doTest(
      s"""val i = 1; ${START}Some(i)$END""",
      """val i = 1; Some(i)""",
      """val i = 1; Option(i)"""
    )
  }

  def testBinaryExpression(): Unit = {
    doTest(
      s"""val i = 1; ${START}Some(i * 2)$END""",
      """val i = 1; Some(i * 2)""",
      """val i = 1; Option(i * 2)"""
    )
  }

  def testConstant(): Unit = {
    doTest(
      s"""${START}Some("constant")$END""",
      """Some("constant")""",
      """Option("constant")"""
    )
  }

}

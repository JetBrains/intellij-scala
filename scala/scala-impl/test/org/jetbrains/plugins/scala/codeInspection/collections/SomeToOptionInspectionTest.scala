package org.jetbrains.plugins.scala
package codeInspection
package collections

import com.intellij.testFramework.EditorTestUtil

/**
  * @author t-kameyama
  */
class SomeToOptionInspectionTest extends OperationsOnCollectionInspectionTest {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[SomeToOptionInspection]

  override protected val hint: String = InspectionBundle.message("replace.with.option")

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

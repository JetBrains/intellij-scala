package org.jetbrains.plugins.scala
package codeInspection
package collections

import com.intellij.testFramework.EditorTestUtil

/**
  * @author t-kameyama
  */
class OptionWithConstantTest extends OperationsOnCollectionInspectionTest {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[OptionWithConstantInspection]

  override protected val hint: String = InspectionBundle.message("replace.with.some")

  def testString(): Unit = {
    doTest(
      s"""${START}Option("constant")$END""",
      """Option("constant")""",
      """Some("constant")"""
    )
  }

  def testInt(): Unit = {
    doTest(
      s"${START}Option(1)$END",
      "Option(1)",
      "Some(1)"
    )
  }

  def testNull(): Unit = {
    checkTextHasNoErrors("Option(null)")
  }

}

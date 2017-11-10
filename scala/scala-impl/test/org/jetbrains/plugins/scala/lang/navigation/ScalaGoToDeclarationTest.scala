package org.jetbrains.plugins.scala.lang.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestAdapter, ScalaLightPlatformCodeInsightTestCaseAdapter}
import org.junit.Assert

/**
  * Nikolay.Tropin
  * 08-Nov-17
  */
class ScalaGoToDeclarationTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  import com.intellij.testFramework.EditorTestUtil.{CARET_TAG => caretTag}

  def testSyntheticApply(): Unit = doTest(
    s"""
       |case class Test(i: Int)
       |
       |object Test {
       |  def apply(boolean: Boolean) = ${caretTag}Test(0)
       |}
      """, Seq("ScClass: Test")
  )

  def testSyntheticUnapply(): Unit = doTest(
    s"""
       |case class Test(i: Int)
       |
       |object Test {
       |  null match {
       |    case ${caretTag}Test(1) =>
       |  }
       |}
      """, Seq("ScClass: Test")
  )

  def testSyntheticCopy(): Unit = doTest(
    s"""
       |case class Test(i: Int)
       |
       |object Test {
       |  Test(1).${caretTag}copy(i = 2)
       |}
      """, Seq("ScClass: Test")
  )

  def testApply(): Unit = doTest(
    s"""
       |case class Test(i: Int)
       |
       |object Test {
       |  def apply(b: Boolean) = Test(0)
       |
       |  ${caretTag}Test(false)
       |}
      """, Seq("ScObject: Test", "ScFunctionDefinition: apply")
  )

  private def doTest(fileText: String, expectedTargets: Seq[String]) = {
    val text = ScalaLightCodeInsightFixtureTestAdapter.normalize(fileText)
    configureFromFileTextAdapter("dummy.scala", text)

    val targets =
      GotoDeclarationAction
        .findAllTargetElements(getProjectAdapter, getEditorAdapter, caretOffset)
        .map(_.toString).toSeq
    Assert.assertEquals("Wrong targets: ", expectedTargets, targets)
  }

  private def caretOffset: Int = getEditorAdapter.getCaretModel.getOffset
}
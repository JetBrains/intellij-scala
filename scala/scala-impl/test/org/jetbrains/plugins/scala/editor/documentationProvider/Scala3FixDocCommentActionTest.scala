package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.codeInsight.editorActions.FixDocCommentAction
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

class Scala3FixDocCommentActionTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  override protected def setUp(): Unit = {
    super.setUp()
    JavaDocTemplateTestUtils.enableJavaDocMethodTemplate(getProject, getTestRootDisposable)
  }

  def testMethodParamAndReturnStub(): Unit = {
    configureFromFileText(
      s"""object Test {
         |  def ${CARET}test(i: Int, s: String): Int = ???
         |}""".stripMargin
    )

    myFixture.performEditorAction(FixDocCommentAction.ACTION_ID)

    myFixture.checkResult(
      s"""object Test {
         |  /**
         |   * $CARET
         |   * @param i
         |   * @param s
         |   * @return
         |   */
         |  def test(i: Int, s: String): Int = ???
         |}""".stripMargin,
      true
    )
  }
}

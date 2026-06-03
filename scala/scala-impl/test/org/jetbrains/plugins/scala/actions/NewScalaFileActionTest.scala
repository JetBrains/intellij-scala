package org.jetbrains.plugins.scala.actions

import com.intellij.ide.actions.TestDialogBuilder
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.{ActionManager, ActionUiKind, AnActionEvent, LangDataKeys}
import com.intellij.psi.PsiManager
import junit.framework.TestCase.assertNotNull
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.{StringExt, inWriteCommandAction}

abstract class NewScalaFileActionTestBase extends ScalaFileTemplateTestBase {
  override protected def setUp(): Unit = {
    super.setUp()

    initFileHeaderTemplate()
  }

  protected def doTest(
    directoryName: String,
    name: String,
    templateName: String,
    expectedText: String,
  ): Unit = {
    val dir = myFixture.getTempDirFixture.findOrCreateDir(directoryName)
    val psiDirectory = PsiManager.getInstance(getProject).findDirectory(dir)

    val action = ActionManager.getInstance().getAction(NewScalaFileAction.ID).asInstanceOf[NewScalaFileAction]
    val view = new TestIdeView(psiDirectory)
    val testAnswers = new TestDialogBuilder.TestAnswers(name, templateName)

    val dataContext = SimpleDataContext.builder()
      .setParent(SimpleDataContext.getProjectContext(getProject))
      .add(LangDataKeys.IDE_VIEW, view)
      .add(TestDialogBuilder.TestAnswers.KEY, testAnswers)
      .build()

    val event = AnActionEvent.createEvent(dataContext, null, "", ActionUiKind.NONE, null)

    inWriteCommandAction {
      action.actionPerformed(event)
    }(getProject)

    val selectedElement = view.getSelectedElement
    assertNotNull("No element was created", selectedElement)

    val file = selectedElement.getContainingFile
    myFixture.openFileInEditor(file.getVirtualFile)
    myFixture.checkResult(expectedText.withNormalizedSeparator)
  }
}

class NewScalaFileActionTest_Scala2 extends NewScalaFileActionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  //we ensure that the behavior is not effected by the setting is ignored in Scala 2
  def testCreateScalaClass_Scala3IndentationBasedSyntaxEnabled(): Unit = {
    getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = true
    doTestCreateScalaClass()
  }

  def testCreateScalaClass_Scala3IndentationBasedSyntaxDisabled(): Unit = {
    getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = false
    doTestCreateScalaClass()
  }

  private def doTestCreateScalaClass(): Unit = {
    doTest(
      directoryName = "example",
      name = "MyClass",
      templateName = ScalaFileTemplateUtil.SCALA_CLASS,
      expectedText =
        s"""package example
           |
           |class MyClass $CARET{
           |
           |}
           |""".stripMargin
    )
  }
}

class NewScalaFileActionTest_Scala3 extends NewScalaFileActionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  //we ensure that the behavior is not effected by the setting is ignored in Scala 2
  def testCreateScalaClass_Scala3IndentationBasedSyntaxEnabled(): Unit = {
    getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = true

    doTest(
      directoryName = "example",
      name = "MyClass",
      templateName = ScalaFileTemplateUtil.SCALA_CLASS,
      expectedText =
        s"""package example
           |
           |class MyClass$CARET
           |""".stripMargin
    )
  }

  def testCreateScalaClass_Scala3IndentationBasedSyntaxDisabled(): Unit = {
    getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = false

    doTest(
      directoryName = "example",
      name = "MyClass",
      templateName = ScalaFileTemplateUtil.SCALA_CLASS,
      expectedText =
        s"""package example
           |
           |class MyClass $CARET{
           |
           |}
           |""".stripMargin
    )
  }
}


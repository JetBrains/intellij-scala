package org.jetbrains.plugins.scala.refactoring.introduceVariable

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiFile, PsiFileFactory}
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.{IdeaTestFixture, JavaCodeInsightTestFixture}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaIntroduceVariableHandler
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaIntroduceVariableHandler.ReplaceTestOptions
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings
import org.junit.Assert
import org.junit.Assert.assertNotNull

class ScalaIntroduceVariableTestFixture(
  project: Project,
  codeStyleSettings: Option[ScalaCodeStyleSettings] = None,
  language: Language = ScalaLanguage.INSTANCE
) extends IdeaTestFixture {
  var psiFile: PsiFile = _
  var editor: Editor = _

  private var oldCodeStyleSettings: ScalaCodeStyleSettings = _

  override def setUp(): Unit = {
    codeStyleSettings.foreach { newSettings =>
      oldCodeStyleSettings = ScalaCodeStyleSettings.getInstance(project).clone.asInstanceOf[ScalaCodeStyleSettings]

      TypeAnnotationSettings.set(project, newSettings)
    }
  }

  override def tearDown(): Unit = {
    val virtualFile = psiFile.getVirtualFile
    if (virtualFile != null) {
      FileEditorManager.getInstance(project).closeFile(virtualFile)
    }

    if (oldCodeStyleSettings != null) {
      TypeAnnotationSettings.set(project, oldCodeStyleSettings)
    }
  }

  def configureFromAnotherFixture(codeInsightFixture: JavaCodeInsightTestFixture): Unit = {
    psiFile = codeInsightFixture.getFile
    editor = codeInsightFixture.getEditor
  }

  def configureFromText(fileText: String): Unit = {
    psiFile = PsiFileFactory.getInstance(project).createFileFromText(s"dummy.scala", language, fileText)
    Assert.assertTrue(psiFile.is[ScalaFile])

    val virtualFile = psiFile.getVirtualFile
    assertNotNull(s"Can't find virtual file for $psiFile", virtualFile)

    editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, virtualFile, 0), false)
    assertNotNull(s"Can't open editor for $virtualFile", editor)

    //apply <selection> and <caret> markers from text
    val document = editor.getDocument
    val caretAndSelectionState = EditorTestUtil.extractCaretAndSelectionMarkers(document)
    EditorTestUtil.setCaretsAndSelection(editor, caretAndSelectionState)
    document.commit(project)
  }

  def invokeIntroduceVariableActionAndGetResult(options: ReplaceTestOptions): Either[String, String]  = {
    val dataContext = createDataContext(options)
    val introduceVariableHandler = new ScalaIntroduceVariableHandler
    try {
      if (options.useInplaceRefactoring.contains(true)) {
        ScalaRefactoringUtil.enableInplaceRefactoringInTests(editor)
      }
      introduceVariableHandler.invoke(project, editor, psiFile, dataContext)
      Right(editor.getDocument.getText)
    } catch {
      case refactoringErrorHintException: CommonRefactoringUtil.RefactoringErrorHintException =>
        Left(refactoringErrorHintException.getMessage)
    }
  }

  private def createDataContext(options: ReplaceTestOptions): DataContext = {
    val dataContextBuilder = SimpleDataContext.builder
    dataContextBuilder.add(ScalaIntroduceVariableHandler.ForcedReplaceTestOptions, options)
    dataContextBuilder.build
  }
}

package org.jetbrains.plugins.scala.decompileToJava

import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.{ActionUiKind, AnAction, AnActionEvent, CommonDataKeys, DataContext, Presentation}
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.{FileTypeIndex, GlobalSearchScope}
import com.intellij.psi.{PsiFile, PsiManager}
import junit.framework.TestCase.assertEquals
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaVersion}
import org.junit.Assert.{assertFalse, assertNotNull, assertTrue}

import scala.jdk.CollectionConverters.CollectionHasAsScala

class ShowDecompiledTastyActionTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  private val ActionId = "Scala.DecompileTasty"

  private def getAction: AnAction = {
    val actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance()
    val registeredAction = actionManager.getAction(ActionId)
    assertNotNull(s"Action '$ActionId' should be registered in the ActionManager", registeredAction)
    registeredAction
  }

  def testActionAvailableForScala3LibraryFile(): Unit = {
    val action = getAction
    val actionPresentation = openFileEditorAndUpdateAction(getProject, action, "CanEqual.scala")
    assertTrue(
      s"Action '$ActionId' should be available in the given context",
      actionPresentation.isEnabledAndVisible
    )

    val event = createActionEvent(getProject, action)
    action.actionPerformed(event)
    
    val editor = FileEditorManager.getInstance(getProject).getSelectedTextEditor
    assertEquals("Opened file name", "CanEqual.tasty", editor.getVirtualFile.getName)
  }

  def testActionNotAvailableForOtherFiles(): Unit = {
    val actionPresentation = openFileEditorAndUpdateAction(getProject, getAction, "Option.scala")
    assertFalse(
      s"Action '$ActionId' should not be enabled in the given context",
      actionPresentation.isEnabled
    )
    assertTrue(
      s"Action '$ActionId' should be visible in the given context",
      actionPresentation.isVisible
    )
  }

  private def openFileEditorAndUpdateAction(project: Project, action: AnAction, scala2SourceFile: String): Presentation = {
    findFileAndOpenEditor(project, scala2SourceFile)
    updateActionWithCurrentlySelectedClass(project, action)
  }

  private def updateActionWithCurrentlySelectedClass(project: Project, action: AnAction): Presentation = {
    val event = createActionEvent(project, action)
    action.update(event)
    event.getPresentation
  }

  private def createActionEvent(project: Project, action: AnAction) = {
    val editor = FileEditorManager.getInstance(project).getSelectedTextEditor
    assertNotNull("There should be a selected editor", editor)

    val psiFile = PsiManager.getInstance(project).findFile(editor.getVirtualFile)
      .ensuring(_ != null, "PsiFile could not be retrieved from the editor's virtual file")
      .asInstanceOf[ScalaFile]

    val firstDefinitionInFile = psiFile.members.head

    val dataContext: DataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PSI_ELEMENT, firstDefinitionInFile)
      .add(CommonDataKeys.PROJECT, project)
      .build()

    val event = AnActionEvent.createEvent(action, dataContext, null, "dummy place", ActionUiKind.TOOLBAR, null)
    event
  }

  private def findFileAndOpenEditor(project: Project, Scala3LibrarySourceFile: String): Unit = {
    val scala3PsiFile = findFile(project, Scala3LibrarySourceFile)
    openFileInEditor(project, scala3PsiFile)
    assertSelectedEditorFileName(project, Scala3LibrarySourceFile)
  }

  private def assertSelectedEditorFileName(project: Project, ScalaLibrarySourceFile: String): Unit = {
    val editor = FileEditorManager.getInstance(project).getSelectedTextEditor
    assertNotNull("There should be an open editor", editor)
    assertEquals(ScalaLibrarySourceFile, editor.getVirtualFile.getName)
  }

  private def openFileInEditor(project: Project, psiFile: PsiFile): Unit = {
    // Open CanEqual.scala file
    invokeAndWait {
      PsiNavigationSupport.getInstance
        .createNavigatable(project, psiFile.getVirtualFile, psiFile.getTextOffset)
        .navigate(true)
    }
  }

  private def closeAllOpenEditors(project: Project): Unit = {
    FileEditorManager.getInstance(project).getSelectedEditors.foreach { fileEditor =>
      FileEditorManager.getInstance(project).closeFile(fileEditor.getFile)
    }
  }

  private def findFile(project: Project, ScalaLibrarySourceFile: String) = {
    FileTypeIndex.getFiles(ScalaFileType.INSTANCE, GlobalSearchScope.everythingScope(project)).asScala
      .find(_.getName == ScalaLibrarySourceFile)
      .map(vf => PsiManager.getInstance(project).findFile(vf))
      .getOrElse(throw new RuntimeException(s"File '$ScalaLibrarySourceFile' not found in the library scope"))
  }
}
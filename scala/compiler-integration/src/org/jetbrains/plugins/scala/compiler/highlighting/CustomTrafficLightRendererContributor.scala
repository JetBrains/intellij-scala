package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer.DaemonCodeAnalyzerStatus
import com.intellij.codeInsight.daemon.impl._
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.UIController
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.compiler.CompilerIntegrationBundle
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode

import java.util.{ArrayList => JArrayList, List => JList}

/**
 * We need this for showing the highlighting compilation progress in the "traffic light".
 */
class CustomTrafficLightRendererContributor
  extends TrafficLightRendererContributor {

  import CustomTrafficLightRendererContributor.FakeHighlightingPass

  override def createRenderer(editor: Editor, file: PsiFile): TrafficLightRenderer = {
    new CustomTrafficLightRenderer(editor, file)
  }

  //noinspection UnstableApiUsage,ApiStatus
  private class CustomTrafficLightRenderer(
    editor: Editor,
    file: PsiFile
  ) extends TrafficLightRenderer(editor.getProject, editor.getDocument) {
    private val project = editor.getProject

    override def getDaemonCodeAnalyzerStatus(severityRegistrar: SeverityRegistrar): DaemonCodeAnalyzerStatus = {
      val status = super.getDaemonCodeAnalyzerStatus(severityRegistrar)

      def isHighlightingCompilerRunning(project: Project): Boolean =
        CompilerHighlightingService.get(project).isCompiling

      if (!project.isDisposed) { // EA-246923
        val compilerHighlightingInProgress =
          isHighlightingCompilerRunning(project) && ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(file)

        val errorAnalyzingFinished = status.errorAnalyzingFinished && !compilerHighlightingInProgress
        status.errorAnalyzingFinished = errorAnalyzingFinished

        if (compilerHighlightingInProgress) {
          val passesField = classOf[DaemonCodeAnalyzerStatus].getDeclaredField("passes")
          passesField.setAccessible(true)
          val oldPasses = passesField.get(status).asInstanceOf[JList[ProgressableTextEditorHighlightingPass]]
          val newPasses = new JArrayList[ProgressableTextEditorHighlightingPass](oldPasses)
          val progress = CompilerGeneratedStateManager.get(project).progress
          newPasses.add(new FakeHighlightingPass(editor, file, progress))
          passesField.set(status, newPasses)
        }
      }

      status
    }

    override def createUIController(): UIController = super.createUIController(editor)
  }

}

object CustomTrafficLightRendererContributor {

  private class FakeHighlightingPass(editor: Editor, file: PsiFile, progress: Double)
    extends ProgressableTextEditorHighlightingPass(
      file.getProject,
      editor.getDocument,
      CompilerIntegrationBundle.message("highlighting.compilation"),
      file,
      editor,
      TextRange.EMPTY_RANGE,
      false,
      new DefaultHighlightInfoProcessor) {

    override def collectInformationWithProgress(progress: ProgressIndicator): Unit = ()

    override def applyInformationWithProgress(): Unit = ()

    override def getProgress: Double = progress
  }
}

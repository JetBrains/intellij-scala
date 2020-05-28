package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer.DaemonCodeAnalyzerStatus
import com.intellij.codeInsight.daemon.impl.{DefaultHighlightInfoProcessor, ProgressableTextEditorHighlightingPass, SeverityRegistrar, TrafficLightRenderer, TrafficLightRendererContributor}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.PsiFile
import java.util.{List => JList}
import java.util.{ArrayList => JArrayList}

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode

/**
 * We need this for showing the highlighting compilation progress in the "traffic light".
 */
class CustomTrafficLightRendererContributor
  extends TrafficLightRendererContributor {

  import CustomTrafficLightRendererContributor.FakeHighlightingPass

  override def createRenderer(editor: Editor, file: PsiFile): TrafficLightRenderer = {
    val project = editor.getProject
    new TrafficLightRenderer(project, editor.getDocument) {
      override def getDaemonCodeAnalyzerStatus(severityRegistrar: SeverityRegistrar): DaemonCodeAnalyzerStatus = {
        val compilerHighlightingInProgress =
          JpsCompiler.get(project).isRunning && ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)
        val status = super.getDaemonCodeAnalyzerStatus(severityRegistrar)
        status.errorAnalyzingFinished = status.errorAnalyzingFinished && !compilerHighlightingInProgress
        if (compilerHighlightingInProgress) {
          val passesField = classOf[DaemonCodeAnalyzerStatus].getDeclaredField("passes")
          passesField.setAccessible(true)
          val oldPasses = passesField.get(status).asInstanceOf[JList[ProgressableTextEditorHighlightingPass]]
          val newPasses = new JArrayList[ProgressableTextEditorHighlightingPass](oldPasses)
          val progress = CompilerGeneratedStateManager.get(project).progress
          newPasses.add(new FakeHighlightingPass(editor, file, progress))
          passesField.set(status, newPasses)
        }
        status
      }
    }
  }
}

object CustomTrafficLightRendererContributor {

  private class FakeHighlightingPass(editor: Editor, file: PsiFile, progress: Double)
    extends ProgressableTextEditorHighlightingPass(
      file.getProject,
      editor.getDocument,
      ScalaBundle.message("highlighting.compilation"),
      file,
      editor,
      file.getTextRange,
      false,
      new DefaultHighlightInfoProcessor) {

    override def collectInformationWithProgress(progress: ProgressIndicator): Unit = ()

    override def applyInformationWithProgress(): Unit = ()

    override def getProgress: Double = progress
  }
}

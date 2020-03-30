package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer.DaemonCodeAnalyzerStatus
import com.intellij.codeInsight.daemon.impl.{DefaultHighlightInfoProcessor, ProgressableTextEditorHighlightingPass, SeverityRegistrar, TrafficLightRenderer, TrafficLightRendererContributor}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.compiler.CompilerLock
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
          CompilerLock.get(project).isLocked && ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)
        val status = super.getDaemonCodeAnalyzerStatus(severityRegistrar)
        status.errorAnalyzingFinished = status.errorAnalyzingFinished && !compilerHighlightingInProgress
        if (compilerHighlightingInProgress) {
          val passStatiField = classOf[DaemonCodeAnalyzerStatus].getDeclaredField("passStati")
          passStatiField.setAccessible(true)
          val oldPassStati = passStatiField.get(status).asInstanceOf[JList[ProgressableTextEditorHighlightingPass]]
          val newPassStati = new JArrayList[ProgressableTextEditorHighlightingPass](oldPassStati)
          val progress = CompilerGeneratedStateManager.get(project).progress
          newPassStati.add(new FakeHighlightingPass(editor, file, progress))
          passStatiField.set(status, newPassStati)
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

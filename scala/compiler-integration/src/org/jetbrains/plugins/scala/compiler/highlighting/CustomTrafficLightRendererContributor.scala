package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer.DaemonCodeAnalyzerStatus
import com.intellij.codeInsight.daemon.impl._
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.{AnalyzerStatus, PassWrapper, UIController}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.PsiJavaFileImpl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.compiler.CompilerIntegrationBundle
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode

import scala.jdk.CollectionConverters._

/**
 * We need this for showing the highlighting compilation progress in the "traffic light".
 */
@ApiStatus.Internal
final class CustomTrafficLightRendererContributor extends TrafficLightRendererContributor {

  override def createRenderer(editor: Editor, file: PsiFile): TrafficLightRenderer = {
    val project = editor.getProject
    if ((project eq null) || project.isDisposed) return null

    val isScalaOrJavaFile = file match {
      case _: ScalaFileImpl | _: PsiJavaFileImpl => true
      case _ => false
    }

    //CBH is only supported in Scala or Java files
    // if we register the renderer for files of any kind it can cause some unexpected issue
    // For example, it can block other plugins unloading (see IDEA-320923)
    if (isScalaOrJavaFile && ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(file))
      new CustomTrafficLightRenderer(project, editor)
    else
      null
  }

  //noinspection ApiStatus,UnstableApiUsage
  private class CustomTrafficLightRenderer(project: Project, editor: Editor) extends TrafficLightRenderer(project, editor) {
    override def fillDaemonCodeAnalyzerErrorsStatus(status: DaemonCodeAnalyzerStatus, severityRegistrar: SeverityRegistrar): Unit = {
      if (project.isDisposed) return
      val compiling = CompilerHighlightingService.get(project).isCompiling
      if (compiling) {
        status.errorAnalyzingFinished = false
      }
    }

    override def getStatus: AnalyzerStatus = {
      val status = super.getStatus
      if (project.isDisposed) return status

      val progress =
        if (CompilerHighlightingService.get(project).isCompiling)
          Some(CompilerGeneratedStateManager.get(project).progress)
        else
          None

      progress match {
        case Some(progress) =>
          val percentage = {
            val p = (progress * 100).toInt
            if (p == 100) 99 else p
          }
          val pass = new PassWrapper(CompilerIntegrationBundle.message("highlighting.compilation"), percentage)
          val oldPasses = status.getPasses.asScala
          val newPasses = (oldPasses :+ pass).asJava
          status.withPasses(newPasses)
        case None => status
      }
    }

    override def createUIController(): UIController = super.createUIController(editor)
  }
}

package org.jetbrains.plugins.scala.compiler.highlighting.core

import com.intellij.codeInsight.daemon.impl.*
import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer.DaemonCodeAnalyzerStatus
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.markup.{AnalyzerStatus, AnalyzingType, InspectionsLevel, InspectionsState, LanguageHighlightLevel, PassWrapper, StatusItem, UIController}
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.source.PsiJavaFileImpl
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.GridBag
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.ModuleKey
import org.jetbrains.plugins.scala.compiler.highlighting.services.{CompilationLogicService, ProjectProgressService}
import org.jetbrains.plugins.scala.compiler.highlighting.util.CompilerHighlightingBundle
import org.jetbrains.plugins.scala.extensions.{inReadAction, invokeLater}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode

import java.awt.event.ActionListener
import java.awt.{Container, GridBagConstraints}
import java.util
import scala.jdk.CollectionConverters.*

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
      new CustomTrafficLightRenderer(project, editor, file.getVirtualFile)
    else
      null
  }

  //noinspection ApiStatus,UnstableApiUsage
  private class CustomTrafficLightRenderer(project: Project, editor: Editor, file: VirtualFile)
    extends TrafficLightRenderer(project, editor) {
    override def fillDaemonCodeAnalyzerErrorsStatus(status: DaemonCodeAnalyzerStatus, severityRegistrar: SeverityRegistrar): Unit = {
      if (project.isDisposed) return
      val compiling = ProjectProgressService(project).isCompiling
      if (compiling) {
        status.errorAnalyzingFinished = false
      }
    }

    override def getStatus: AnalyzerStatus = {
      val status = super.getStatus
      if (project.isDisposed) return status

      val progressService = ProjectProgressService(project)
      val progress =
        if (progressService.isCompiling)
          Some(progressService.compilationProgress)
        else
          None

      progress match {
        case Some(progress) =>
          val percentage = {
            val p = (progress * 100).toInt
            if (p == 100) 99 else p
          }
          val pass = new PassWrapper(CompilerHighlightingBundle.message("highlighting.compilation"), percentage)
          val oldPasses = status.getPasses.asScala
          val newPasses = (oldPasses :+ pass).asJava
          status.withPasses(newPasses)
        case None => blockedStatus(status).getOrElse(status)
      }
    }

    /**
     * The status of a file whose target the last compilation never reached, because it stopped at errors in
     * other targets.
     *
     * Without it the traffic light shows the check of a file with no problems, for a file nothing was compiled
     * for. Problems found by other passes are still shown, next to a pause icon; the title says that the
     * compiler's are missing.
     */
    private def blockedStatus(status: AnalyzerStatus): Option[AnalyzerStatus] =
      if (file eq null) None
      else {
        val blockers = CompilationLogicService(project).blockersOf(ModuleKey.of(project, file))
        Option.when(blockers.nonEmpty) {
          val names = BlockedTargetsPresentation.blockerNames(blockers)
          val title = CompilerHighlightingBundle.message("highlighting.blocked.status.title")
          val details = CompilerHighlightingBundle.message("highlighting.blocked.status.details", names, BlockedTargetsPresentation.blockerCount(blockers))
          if (status.getShowNavigation) {
            // Other passes found problems: keep their counts and navigation, and say what is missing.
            val paused = new StatusItem(CompilerHighlightingBundle.message("highlighting.blocked.status.text"), AllIcons.General.InspectionsPause, details)
            val blocked = new AnalyzerStatus(status.getIcon, title, details, new BlockedUIController(status.getController))
              .withNavigation(true)
              .withExpandedStatus((status.getExpandedStatus.asScala :+ paused).asJava)
              .withPasses(status.getPasses)
              .withAnalyzingType(AnalyzingType.PARTIAL)
            Option(status.getInspectionsState).fold(blocked)(blocked.withState)
          } else
            new AnalyzerStatus(AllIcons.General.InspectionsPause, title, details, new BlockedUIController(status.getController))
              .withState(InspectionsState.PAUSED)
              // An item rather than a text status, which would show the text without the icon.
              .withExpandedStatus(util.List.of(new StatusItem(
                CompilerHighlightingBundle.message("highlighting.blocked.status.text"), AllIcons.General.InspectionsPause, details)))
              .withAnalyzingType(AnalyzingType.SUSPENDED)
        }
      }

    override def createUIController(): UIController = super.createUIController(editor)

    /**
     * The platform's popup, with a link to the errors of the blockers at its bottom while the file is blocked.
     *
     * The platform's controller can't be replaced: the one for a renderer with an editor comes from the final
     * `createUIController(editor)`. So this wraps it, and is put in the blocked status instead. It holds no state
     * of its own, so a new one for every status is fine.
     */
    private final class BlockedUIController(delegate: UIController) extends UIController {
      override def isToolbarEnabled: Boolean = delegate.isToolbarEnabled
      override def getActions: util.List[AnAction] = delegate.getActions
      override def getAvailableLevels: util.List[InspectionsLevel] = delegate.getAvailableLevels
      override def getHighlightLevels: util.List[LanguageHighlightLevel] = delegate.getHighlightLevels
      override def setHighLightLevel(newLevel: LanguageHighlightLevel): Unit = delegate.setHighLightLevel(newLevel)
      override def canClosePopup: Boolean = delegate.canClosePopup
      override def onClosePopup(): Unit = delegate.onClosePopup()
      override def toggleProblemsView(): Unit = delegate.toggleProblemsView()

      override def fillHectorPanels(container: Container, gc: GridBag): Unit = {
        delegate.fillHectorPanels(container, gc)
        val blockers =
          if ((file eq null) || project.isDisposed) Set.empty
          else CompilationLogicService(project).blockersOf(ModuleKey.of(project, file))
        if (blockers.nonEmpty) {
          val names = BlockedTargetsPresentation.blockerNames(blockers)
          val link = new ActionLink(
            CompilerHighlightingBundle.message("highlighting.blocked.status.show.errors", names),
            (_ => BlockedTargetsPresentation.showErrors(project, blockers)): ActionListener
          )
          container.add(link, gc.next().anchor(GridBagConstraints.LINE_END))
        }
      }
    }
  }
}

private object CustomTrafficLightRendererContributor {

  /** Repaints every traffic light of the project once what is shown about blocked targets changes. */
  private final class Repainter(project: Project) extends BlockedTargetsListener {
    override def blockedChanged(): Unit =
      invokeLater {
        if (!project.isDisposed) {
          val documents = PsiDocumentManager.getInstance(project)
          val stripes = ErrorStripeUpdateManager.getInstance(project)
          EditorFactory.getInstance().getAllEditors
            .filter(_.getProject == project)
            .foreach { editor =>
              val psiFile = inReadAction(documents.getPsiFile(editor.getDocument))
              if (psiFile ne null) stripes.launchRepaintErrorStripePanel(editor, psiFile)
            }
        }
      }
  }
}

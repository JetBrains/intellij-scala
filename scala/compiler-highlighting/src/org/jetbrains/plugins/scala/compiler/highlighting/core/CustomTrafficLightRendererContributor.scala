package org.jetbrains.plugins.scala.compiler.highlighting.core

import com.intellij.codeInsight.daemon.impl.*
import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer.DaemonCodeAnalyzerStatus
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.markup.{AnalyzerStatus, InspectionsLevel, InspectionsState, LanguageHighlightLevel, PassWrapper, StatusItem, UIController}
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
import javax.swing.Icon
import scala.jdk.CollectionConverters.*

/**
 * We need this for showing the highlighting compilation progress in the "traffic light".
 */
@ApiStatus.Internal
final class CustomTrafficLightRendererContributor extends TrafficLightRendererContributor {
  import CustomTrafficLightRendererContributor.{BlockedIcon, BlockedItemIcon}

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
     * for. "Highlighting Blocked" leads the traffic light, before any problems other passes found; the popup
     * names the blockers and says what unblocks it.
     *
     * The analyzing type is left as it was, so the blocked item is not dimmed like the counts of an unfinished
     * analysis.
     */
    private def blockedStatus(status: AnalyzerStatus): Option[AnalyzerStatus] =
      if (file eq null) None
      else {
        val blockers = CompilationLogicService(project).blockersOf(ModuleKey.of(project, file))
        Option.when(blockers.nonEmpty) {
          val title = CompilerHighlightingBundle.message("highlighting.blocked.status.title",
            BlockedTargetsPresentation.blockerNames(blockers))
          val details = CompilerHighlightingBundle.message("highlighting.blocked.status.details")
          // An item rather than a text status, which would show the text without the icon.
          val blocked = new StatusItem(CompilerHighlightingBundle.message("highlighting.blocked.status.text"), BlockedItemIcon, details)
          val controller = new BlockedUIController(status.getController)
          if (status.getShowNavigation) {
            // Other passes found problems: keep their counts and navigation.
            val withCounts = new AnalyzerStatus(status.getIcon, title, details, controller)
              .withNavigation(true)
              .withExpandedStatus((blocked +: status.getExpandedStatus.asScala).asJava)
              .withPasses(status.getPasses)
              .withAnalyzingType(status.getAnalyzingType)
            Option(status.getInspectionsState).fold(withCounts)(withCounts.withState)
          } else
            new AnalyzerStatus(BlockedIcon, title, details, controller)
              .withState(InspectionsState.PAUSED)
              .withExpandedStatus(util.List.of(blocked))
        }
      }

    override def createUIController(): UIController = super.createUIController(editor)

    /**
     * The platform's popup, with a link to the first error of the blockers at its bottom while the file is blocked.
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
          val link = new ActionLink(
            CompilerHighlightingBundle.message("highlighting.blocked.status.show.errors"),
            (_ => BlockedTargetsPresentation.showErrors(project, blockers)): ActionListener
          )
          container.add(link, gc.next().anchor(GridBagConstraints.LINE_END))
        }
      }
    }
  }
}

private object CustomTrafficLightRendererContributor {

  /**
   * A pause icon, shown as already pressed: highlighting is paused, not waiting for a click.
   *
   * The pause action's icon rather than the inspections' one, whose bars are half a pixel off center, which the
   * background makes visible.
   */
  private val BlockedIcon: Icon = new PressedIcon(AllIcons.Actions.Pause)

  /** [[BlockedIcon]] with room before the text it is shown with. */
  private val BlockedItemIcon: Icon = new PressedIcon(AllIcons.Actions.Pause, gapAfter = 3)

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

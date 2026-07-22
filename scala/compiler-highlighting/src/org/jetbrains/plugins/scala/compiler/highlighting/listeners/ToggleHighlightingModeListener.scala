package org.jetbrains.plugins.scala.compiler.highlighting.listeners

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ModuleRootEvent, ModuleRootListener}
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.resolve.ResolveCache
import org.jetbrains.plugins.scala.annotator.hints.AnnotatorHints
import org.jetbrains.plugins.scala.compiler.CompileServerNotificationsService
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.HighlightingTriggerPhaseEvent
import org.jetbrains.plugins.scala.compiler.highlighting.services.BackgroundExecutorService.executeOnBackgroundThreadInNotDisposed
import org.jetbrains.plugins.scala.compiler.highlighting.services.ExternalHighlightersService
import org.jetbrains.plugins.scala.compiler.highlighting.triggers.OnEditorSelectedTrigger
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.extensions.{inReadAction, inWriteAction, invokeLater, invokeWhenSmart}
import org.jetbrains.plugins.scala.settings.{CompilerHighlightingListener, ScalaHighlightingMode}

/**
 * Ensures correct toggling between "standard" and "compiler-based" highlighting modes.
 * Toggling means that the value of 
 * [[ScalaHighlightingMode.isShowErrorsFromCompilerEnabled]] was changed.
 */
abstract class ToggleHighlightingModeListener(project: Project) {
  protected def compileOrEraseHighlightings(triggerSource: String): Unit =
    invokeWhenSmart(project) {
      executeOnBackgroundThreadInNotDisposed(project) {
        if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
          inReadAction(AnnotatorHints.clearIn(project))
        } else {
          ExternalHighlightersService.instance(project).eraseAllHighlightings()
        }
        // TODO: we should ensure that we do not do this if the project wasn't highlighted with compiler at all,
        //  e.g. for Scala 2 projects where it's disabled by default
        // Passing `ModalityState.nonModal()` is important here. The call to `forceStandardHighlighting` requires
        // making changes to the PSI model, which must be done in a write-safe context.
        // Per the documentation of `com.intellij.openapi.application.TransactionGuard`, `nonModal` is one of the
        // write-safe contexts.
        invokeLater(ModalityState.nonModal()) {
          forceStandardHighlighting(project)
          CompileServerNotificationsService.get(project).resetNotifications()
          if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
            val requestId = TriggerPhaseEvents.newRequestId()
            Tracing(project).instant(HighlightingTriggerPhaseEvent(requestId, triggerSource))
            OnEditorSelectedTrigger.trigger(project, requestId)
          }
        }
      }
    }
  
  private def forceStandardHighlighting(project: Project): Unit = inWriteAction {
    ResolveCache.getInstance(project).clearCache(true)
    PsiManager.getInstance(project).dropPsiCaches()
    DaemonCodeAnalyzer.getInstance(project).restart("Restart because highlighting was toggled")
  }
}

object ToggleHighlightingModeListener {
  final class OnCompilerHighlightingChange(project: Project) extends ToggleHighlightingModeListener(project) with CompilerHighlightingListener {
    override def compilerHighlightingScala2Changed(enabled: Boolean): Unit =
      compileOrEraseHighlightings("highlighting toggled")

    override def compilerHighlightingScala3Changed(enabled: Boolean): Unit =
      compileOrEraseHighlightings("highlighting toggled")
  }

  final class OnModuleRootChange(project: Project) extends ToggleHighlightingModeListener(project) with ModuleRootListener {
    override def rootsChanged(event: ModuleRootEvent): Unit =
      compileOrEraseHighlightings("module roots changed")
  }
}

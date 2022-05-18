package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.{DumbService, Project, ProjectManagerListener}
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.resolve.ResolveCache
import org.jetbrains.plugins.scala.annotator.hints.AnnotatorHints
import org.jetbrains.plugins.scala.compiler.CompileServerNotificationsService
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.settings.CompilerHighlightingListener

/**
 * Ensures correct toggling between "standard" and "compiler-based" highlighting modes.
 * Toggling means that the value of 
 * [[ScalaHighlightingMode.isShowErrorsFromCompilerEnabled]] was changed.
 */
class ToggleHighlightingModeListener
  extends ProjectManagerListener {
  
  override def projectOpened(project: Project): Unit = if (!ApplicationManager.getApplication.isUnitTestMode) {

    project.subscribeToModuleRootChanged() { _ => compileOrEraseHighlightings(s"project roots changed", project) }

    object listener extends CompilerHighlightingListener {
      override def compilerHighlightingScala2Changed(enabled: Boolean): Unit =
        compileOrEraseHighlightings(s"highlighting mode toggled (Scala 2)", project)
      override def compilerHighlightingScala3Changed(enabled: Boolean): Unit =
        compileOrEraseHighlightings(s"highlighting mode toggled (Scala 3)", project)
    }
    
    ScalaHighlightingMode.addSettingsListener(project)(listener)
  }
  
  private def compileOrEraseHighlightings(debugReason: String, project: Project): Unit =
    DumbService.getInstance(project).runWhenSmart { () =>
      if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
        val modules = ScalaHighlightingMode.compilerBasedHighlightingModules(project)
        CompilerHighlightingService.get(project).triggerIncrementalCompilation(debugReason, modules, delayedProgressShow = false)
        AnnotatorHints.clearIn(project)
      } else {
        ExternalHighlighters.eraseAllHighlightings(project)
      }
      // TODO: we should ensure that we do not do this if the project wasn't highlighted with compiler at all,
      //  e.g. for Scala 2 projects where it's disabled by default
      forceStandardHighlighting(project)
      CompileServerNotificationsService.get(project).resetNotifications()
    }
  
  private def forceStandardHighlighting(project: Project): Unit = {
    ResolveCache.getInstance(project).clearCache(true)
    PsiManager.getInstance(project).dropPsiCaches()
    DaemonCodeAnalyzer.getInstance(project).restart()
  }
}

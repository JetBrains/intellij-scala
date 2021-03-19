package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.{DumbService, Project, ProjectManagerListener}
import com.intellij.openapi.util.registry.{RegistryValue, RegistryValueListener}
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiModificationTrackerImpl
import com.intellij.psi.impl.source.resolve.ResolveCache
import org.jetbrains.plugins.scala.annotator.hints.AnnotatorHints
import org.jetbrains.plugins.scala.compiler.CompileServerNotificationsService
import org.jetbrains.plugins.scala.project.ProjectExt

/**
 * Ensures correct toggling between "standard" and "compiler-based" highlighting modes.
 * Toggling means that the value of 
 * [[ScalaHighlightingMode.isShowErrorsFromCompilerEnabled]] was changed.
 */
class ToggleHighlightingModeListener
  extends ProjectManagerListener {
  
  override def projectOpened(project: Project): Unit = if (!ApplicationManager.getApplication.isUnitTestMode) {
    project.subscribeToModuleRootChanged() { _ => compileOrEraseHighlightings(project) }
    ScalaHighlightingMode.addRegistryListener(project)(new RegistryValueListener {
      override def afterValueChanged(value: RegistryValue): Unit = compileOrEraseHighlightings(project)
    })
  }
  
  private def compileOrEraseHighlightings(project: Project): Unit =
    DumbService.getInstance(project).runWhenSmart { () =>
      if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
        CompilerHighlightingService.get(project).triggerIncrementalCompilation(delayedProgressShow = false)
        AnnotatorHints.clearIn(project)
      } else {
        ExternalHighlighters.eraseAllHighlightings(project)
      }
      forceStandardHighlighting(project)
      CompileServerNotificationsService.get(project).resetNotifications()
    }
  
  private def forceStandardHighlighting(project: Project): Unit = {
    ResolveCache.getInstance(project).clearCache(true)
    PsiManager.getInstance(project).dropPsiCaches()
    DaemonCodeAnalyzer.getInstance(project).restart()
  }
}

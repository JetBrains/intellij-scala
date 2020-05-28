package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.{DumbService, Project, ProjectManagerListener}
import com.intellij.openapi.util.registry.{RegistryValue, RegistryValueListener}
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiModificationTrackerImpl
import com.intellij.psi.impl.source.resolve.ResolveCache
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode
import org.jetbrains.plugins.scala.annotator.hints.AnnotatorHints
import org.jetbrains.plugins.scala.project.ProjectExt

/**
 * Ensures correct toggling between "standard" and "compiler-based" highlighting modes.
 * Toggling means that the value of 
 * [[org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode.isShowErrorsFromCompilerEnabled]] was changed.
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
        JpsCompiler.get(project).rescheduleCompilation(testScopeOnly = false)
        AnnotatorHints.clearIn(project)
      } else {
        ExternalHighlighters.eraseAllHighlightings(project)
      }
      forceStandradHighlighting(project)
    }
  
  private def forceStandradHighlighting(project: Project): Unit = {
    ResolveCache.getInstance(project).clearCache(true)
    PsiManager.getInstance(project).getModificationTracker.asInstanceOf[PsiModificationTrackerImpl].incCounter()
    DaemonCodeAnalyzer.getInstance(project).restart()
  }
}

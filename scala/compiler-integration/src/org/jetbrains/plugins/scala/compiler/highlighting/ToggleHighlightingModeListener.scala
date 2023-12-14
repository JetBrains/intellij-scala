package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.{DumbService, Project, ProjectManagerListener}
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.resolve.ResolveCache
import org.jetbrains.plugins.scala.annotator.hints.AnnotatorHints
import org.jetbrains.plugins.scala.compiler.CompileServerNotificationsService
import org.jetbrains.plugins.scala.compiler.highlighting.BackgroundExecutorService.executeOnBackgroundThreadInNotDisposed
import org.jetbrains.plugins.scala.extensions.{inReadAction, inWriteAction, invokeLater}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.settings.{CompilerHighlightingListener, ScalaHighlightingMode}

/**
 * Ensures correct toggling between "standard" and "compiler-based" highlighting modes.
 * Toggling means that the value of 
 * [[ScalaHighlightingMode.isShowErrorsFromCompilerEnabled]] was changed.
 */
private final class ToggleHighlightingModeListener extends ProjectManagerListener {
  
  override def projectOpened(project: Project): Unit = if (!ApplicationManager.getApplication.isUnitTestMode) {

    project.subscribeToModuleRootChanged() { _ => compileOrEraseHighlightings(project) }

    val listener = new CompilerHighlightingListener {
      override def compilerHighlightingScala2Changed(enabled: Boolean): Unit =
        compileOrEraseHighlightings(project)
      override def compilerHighlightingScala3Changed(enabled: Boolean): Unit =
        compileOrEraseHighlightings(project)
    }

    ScalaHighlightingMode.addSettingsListener(project)(listener)
  }
  
  private def compileOrEraseHighlightings(project: Project): Unit =
    DumbService.getInstance(project).runWhenSmart { () =>
      executeOnBackgroundThreadInNotDisposed(project) {
        if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
          inReadAction(AnnotatorHints.clearIn(project))
        } else {
          ExternalHighlightersService.instance(project).eraseAllHighlightings()
        }
        // TODO: we should ensure that we do not do this if the project wasn't highlighted with compiler at all,
        //  e.g. for Scala 2 projects where it's disabled by default
        invokeLater {
          forceStandardHighlighting(project)
          CompileServerNotificationsService.get(project).resetNotifications()
          if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
            executeOnBackgroundThreadInNotDisposed(project) {
              Option(FileEditorManager.getInstance(project).getSelectedEditor)
                .flatMap(editor => Option(editor.getFile))
                .foreach(TriggerCompilerHighlightingService.get(project).triggerOnEditorFocus)
            }
          }
        }
      }
    }
  
  private def forceStandardHighlighting(project: Project): Unit = inWriteAction {
    ResolveCache.getInstance(project).clearCache(true)
    PsiManager.getInstance(project).dropPsiCaches()
    DaemonCodeAnalyzer.getInstance(project).restart()
  }
}

package org.jetbrains.plugins.scala.components

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer

/**
 * Runs an activity when a project is opened in the first time or plugin is dynamically loaded.
 * It is started on background thread under "Loading project" dialog.
 *
 * Allows to add a cleanup method which is invoked when application is closing or plugin is unloaded.
 *
 * Register in plugin.xml as `postStartupActivity` extension.
 */
abstract class RunOnceStartupActivity extends StartupActivity.DumbAware with Disposable {

  Disposer.register(ApplicationManager.getApplication.getService(classOf[RunOnceStartupActivityService]), this)

  private var wasAlreadyRun = false

  protected def doRunActivity(): Unit

  protected def doCleanup(): Unit

  override def runActivity(project: Project): Unit = {
    if (!wasAlreadyRun) {
      doRunActivity()
      wasAlreadyRun = true
    }
  }

  override final def dispose(): Unit = doCleanup()
}

@Service
private final class RunOnceStartupActivityService extends Disposable {
  override def dispose(): Unit = {}
}
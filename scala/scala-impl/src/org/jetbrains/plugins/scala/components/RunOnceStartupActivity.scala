package org.jetbrains.plugins.scala.components

import com.intellij.ide.plugins.{DynamicPluginListener, IdeaPluginDescriptor}
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.scala.components.RunOnceStartupActivity.allRegisteredActivities

import scala.collection.mutable.ArrayBuffer

/**
 * Runs an activity when a project is opened in the first time or plugin is dynamically loaded.
 * It is started on background thread under "Loading project" dialog.
 *
 * Allows to add a cleanup method which is invoked when application is closing or plugin is unloaded.
 *
 * Register in plugin.xml as `postStartupActivity` extension.
 */
abstract class RunOnceStartupActivity extends StartupActivity.DumbAware with Disposable {
  allRegisteredActivities += this

  Disposer.register(ApplicationManager.getApplication, this)

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

private object RunOnceStartupActivity extends DynamicPluginListener {
  private val allRegisteredActivities: ArrayBuffer[RunOnceStartupActivity] =
    ArrayBuffer.empty

  override def pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean): Unit = {
    if (pluginDescriptor.getPluginClassLoader == getClass.getClassLoader) {
      allRegisteredActivities.foreach(_.dispose())
    }
  }
}
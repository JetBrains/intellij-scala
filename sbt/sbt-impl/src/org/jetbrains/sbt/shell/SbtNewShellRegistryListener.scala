package org.jetbrains.sbt.shell

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.{Registry, RegistryValue, RegistryValueListener}
import org.jetbrains.plugins.scala.startup.ProjectActivity
import org.jetbrains.plugins.scala.util.UnloadAwareDisposable

/**
 * Registers a listener for the "sbt.new.shell" registry key when a project is opened.
 * If the registry value changes, the sbt shell for the current project is terminated so that
 * a new shell can be started with the updated mode. Due to this, the running
 * shell is in sync with the registry value and can be used safely in multiple places.
 *
 * @todo (ATTENTION) If multiple projects are open and the registry value changes, the sbt shell is killed
 *       in all of them. This is not ideal for production. A better approach would be to store,
 *       within the shell architecture, the mode with which a particular shell is running.
 *       Then, when the registry key changes, the new value would only be applied when starting
 *       a new shell; existing shells could keep running with their original mode.
 */
class SbtNewShellRegistryListener extends ProjectActivity {
  override def execute(project: Project): Unit = {
    val disposable = UnloadAwareDisposable.forProject(project)
    val registry = Registry.get("sbt.new.shell")
    val sbtProcessManager = SbtProcessManager.forProject(project)
    registry.addListener(new RegistryValueListener {
      override def beforeValueChanged(value: RegistryValue): Unit = {
        sbtProcessManager.destroyProcess()
      }
    }, disposable)
  }
}

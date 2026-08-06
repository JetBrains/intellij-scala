package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.startup.ProjectActivity

import javax.swing.KeyStroke

private final class ConfigureShortcutsProjectActivity extends ProjectActivity {
  override def execute(project: Project): Unit = {
    // Application-level services are initialised exactly once.
    ApplicationManager.getApplication.getService(classOf[ConfigureShortcutsProjectActivity.AppService])
  }
}

private object ConfigureShortcutsProjectActivity {
  @Service(Array(Service.Level.APP))
  private final class AppService {
    // Executed in the service constructor.
    registerShortcuts()

    private def registerShortcuts(): Unit = {
      //noinspection ApiStatus,UnstableApiUsage
      if (!ClientSystemInfo.isWindows && !ClientSystemInfo.isMac) { // Workaround for SCL-21346
        val keymap = KeymapManager.getInstance.getActiveKeymap
        keymap.removeShortcut("ZoomInIdeAction", new KeyboardShortcut(KeyStroke.getKeyStroke("shift control alt EQUALS"), null))
        keymap.removeShortcut("ZoomOutIdeAction", new KeyboardShortcut(KeyStroke.getKeyStroke("shift control alt MINUS"), null))
        keymap.removeShortcut("ResetIdeScaleAction", new KeyboardShortcut(KeyStroke.getKeyStroke("shift control alt 0"), null))
      }

      ImplicitShortcuts.setShortcuts(ShowImplicitHintsAction.Id, ImplicitShortcuts.EnableShortcuts)
    }
  }
}

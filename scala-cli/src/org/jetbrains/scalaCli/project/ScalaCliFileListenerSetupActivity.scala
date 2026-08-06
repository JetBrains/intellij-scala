package org.jetbrains.scalaCli.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.startup.ProjectActivity

class ScalaCliFileListenerSetupActivity extends ProjectActivity {
  override def execute(project: Project): Unit = {
    val fileListener = new ScalaCliFileListener(project)
    VirtualFileManager.getInstance().addAsyncFileListener(fileListener, project.unloadAwareDisposable)
  }
}

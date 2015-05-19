package org.jetbrains.sbt.project.autoimport

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * @author Nikolay Obedin
 * @since 3/23/15.
 */
class SbtAutoImportComponent(project: Project) extends AbstractProjectComponent(project) {
  override def projectOpened(): Unit = {
    VirtualFileManager.getInstance().addVirtualFileListener(new SbtAutoImportListener(project), project)
  }
}

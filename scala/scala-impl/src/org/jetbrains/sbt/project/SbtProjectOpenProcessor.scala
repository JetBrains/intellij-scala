package org.jetbrains.sbt
package project

import com.intellij.ide.impl.ProjectUtilKt.runUnderModalProgressIfIsEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor

import javax.swing.Icon

class SbtProjectOpenProcessor extends ProjectOpenProcessor {

  //noinspection ReferencePassedToNls
  override def getName: String = Sbt.Name
  override def getIcon: Icon = Sbt.Icon

  override def canOpenProject(file: VirtualFile): Boolean =
    SbtProjectImportProvider.canImport(file)

  override def doOpenProject(virtualFile: VirtualFile, projectToClose: Project, forceOpenInNewFrame: Boolean): Project =
    runUnderModalProgressIfIsEdt { continuation =>
      new SbtOpenProjectProvider().openProject(virtualFile, projectToClose, forceOpenInNewFrame, continuation)
    }
}

package org.jetbrains.plugins.scala

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ContentEntry, ModifiableRootModel}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.scala.base.libraryLoaders.LibraryLoader
import org.jetbrains.plugins.scala.debugger.ScalaVersion

/**
  * Nikolay.Tropin
  * 22-Sep-17
  */
abstract class DelegatingProjectDescriptor(val delegate: LightProjectDescriptor) extends LightProjectDescriptor {
  override def setUpProject(project: Project, handler: LightProjectDescriptor.SetupHandler) =
    delegate.setUpProject(project, handler)

  override def createSourcesRoot(module: Module) =
    delegate.createSourcesRoot(module)

  override def getModuleType =
    delegate.getModuleType

  override def createMainModule(project: Project) =
    delegate.createMainModule(project)

  override def getSdk =
    delegate.getSdk
}
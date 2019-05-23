package org.jetbrains.plugins.scala.base

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.{Module, ModuleType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightProjectDescriptor

/**
  * Nikolay.Tropin
  * 22-Sep-17
  */
abstract class DelegatingProjectDescriptor(val delegate: LightProjectDescriptor)(newModuleCallback: Module => Unit) extends LightProjectDescriptor {

  class ModuleCapturingHandlerDelegate(handlerDelegate: LightProjectDescriptor.SetupHandler) extends LightProjectDescriptor.SetupHandler {
    override def moduleCreated(module: Module): Unit = {
      handlerDelegate.moduleCreated(module)
      newModuleCallback(module)
    }

    override def sourceRootCreated(sourceRoot: VirtualFile): Unit = handlerDelegate.sourceRootCreated(sourceRoot)
  }

  override def setUpProject(project: Project, handler: LightProjectDescriptor.SetupHandler): Unit = {
    delegate.setUpProject(project, new ModuleCapturingHandlerDelegate(handler))
  }

  override def createSourcesRoot(module: Module): VirtualFile =
    delegate.createSourcesRoot(module)

  override def getModuleType: ModuleType[_ <: ModuleBuilder] =
    delegate.getModuleType

  override def createMainModule(project: Project): Module =
    delegate.createMainModule(project)

  override def getSdk: Sdk =
    delegate.getSdk
}

object DelegatingProjectDescriptor {
  def withAfterSetupProject(delegate: LightProjectDescriptor)(callback: Module => Unit): LightProjectDescriptor = {
    new DelegatingProjectDescriptor(delegate)(callback) { }
  }
  def withAfterSetupProjectJava(delegate: LightProjectDescriptor)(callback: ModuleCallback): LightProjectDescriptor = {
    new DelegatingProjectDescriptor(delegate)(callback.run) { }
  }
}

trait ModuleCallback {
  def run(module: Module)
}
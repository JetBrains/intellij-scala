package org.jetbrains.plugins.scala

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.ThrowableRunnable

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

object DelegatingProjectDescriptor {
  def withAfterSetupProject(delegate: LightProjectDescriptor)(work: ThrowableRunnable[Nothing]): LightProjectDescriptor = {
    new DelegatingProjectDescriptor(delegate) {
      override def setUpProject(project: Project, handler: LightProjectDescriptor.SetupHandler): Unit = {
        super.setUpProject(project, handler)
        WriteAction.run(work)
      }
    }
  }
}
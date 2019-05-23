package org.jetbrains.plugins.scala.base

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightProjectDescriptor

class ScalaLightProjectDescriptor extends LightProjectDescriptor {

  private var myModule: Module = _

  class SetupHandlerDelegate(delegate: LightProjectDescriptor.SetupHandler) extends LightProjectDescriptor.SetupHandler {
    override def moduleCreated(module: Module): Unit = {
      delegate.moduleCreated(module)
      myModule = module
//      tuneModule(module)
    }

    override def sourceRootCreated(sourceRoot: VirtualFile): Unit = delegate.sourceRootCreated(sourceRoot)
  }

  override def setUpProject(project: Project, handler: LightProjectDescriptor.SetupHandler): Unit = {
    super.setUpProject(project, new SetupHandlerDelegate(handler))
    tuneModule(myModule)
    myModule = null
  }

  def tuneModule(module: Module): Unit = ()

}

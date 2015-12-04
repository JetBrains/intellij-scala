package org.jetbrains.sbt.execution

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerationHandler
import org.jetbrains.sbt.settings.SbtSystemSettings

/**
  * @author Nikolay.Tropin
  */
class SbtOrderEnumeratorHandler extends OrderEnumerationHandler {
  override def shouldProcessDependenciesRecursively(): Boolean = false
}

class SbtOrderEnumeratorHandlerFactory extends OrderEnumerationHandler.Factory {
  override def createHandler(module: Module): OrderEnumerationHandler = new SbtOrderEnumeratorHandler

  override def isApplicable(project: Project): Boolean = {
    ModuleManager.getInstance(project).getModules.exists(isApplicable)
  }

  override def isApplicable(module: Module): Boolean = {
    SbtSystemSettings.getInstance(module.getProject).getLinkedProjectSettings(module).isDefined
  }
}
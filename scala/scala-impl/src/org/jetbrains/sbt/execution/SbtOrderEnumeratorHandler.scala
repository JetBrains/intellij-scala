package org.jetbrains.sbt.execution

import java.util

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerationHandler.AddDependencyType
import com.intellij.openapi.roots.impl.ModuleOrderEnumerator
import com.intellij.openapi.roots._
import com.intellij.util.CommonProcessors
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.project.SbtProjectSystem

/**
  * @author Nikolay.Tropin
  */
class SbtOrderEnumeratorHandler extends OrderEnumerationHandler {
  override def shouldAddDependency(orderEntry: OrderEntry, settings: OrderEnumeratorSettings): AddDependencyType = {
    (orderEntry, settings) match {
      case (library: LibraryOrderEntry, enumerator: ModuleOrderEnumerator) =>
        val isTransitive = getModuleFromEnumerator(enumerator).fold(false)(_ != library.getOwnerModule)
        if (isTransitive) AddDependencyType.DO_NOT_ADD else AddDependencyType.DEFAULT
      case _ =>
        AddDependencyType.DEFAULT
    }
  }

  private def getModuleFromEnumerator(enumerator: ModuleOrderEnumerator): Option[Module] = {
    // This method assumes that `processRootModules` in `ModuleOrderEnumerator` calls
    // given processor only on module extracted from its underlying `ModuleRootModel`.
    // If this behaviour is subject to change, it's better to roll back to reflection calls to inner fields.
    import scala.jdk.CollectionConverters._
    val modules = new util.ArrayList[Module]()
    enumerator.processRootModules(new CommonProcessors.CollectProcessor[Module](modules))
    modules.asScala.headOption
  }
}

class SbtOrderEnumeratorHandlerFactory extends OrderEnumerationHandler.Factory {
  override def createHandler(module: Module): OrderEnumerationHandler = new SbtOrderEnumeratorHandler

  override def isApplicable(module: Module): Boolean =
    SbtUtil.isSbtModule(module)
}
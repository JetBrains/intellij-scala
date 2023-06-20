package org.jetbrains.sbt.execution

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderEnumerationHandler.AddDependencyType
import com.intellij.openapi.roots._
import com.intellij.openapi.roots.impl.ModuleOrderEnumerator
import com.intellij.util.CommonProcessors
import org.jetbrains.sbt.SbtUtil

import java.util

/**
 * ATTENTION: implementation should be in sync with<br>
 * org.jetbrains.jps.incremental.scala.model.JpsSbtDependenciesEnumerationHandler
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
  override def shouldAddRuntimeDependenciesToTestCompilationClasspath: Boolean =
    false

  //TODO: sbt doesn't copy resources which are located near main sources to the `target/scala-xy/classes` folder
  //  but looks like simply changing this method return value to `true` doesn't help, investigate...
  override def areResourceFilesFromSourceRootsCopiedToOutput: Boolean =
    super.areResourceFilesFromSourceRootsCopiedToOutput

  override def shouldIncludeTestsFromDependentModulesToTestClasspath: Boolean =
    super.shouldIncludeTestsFromDependentModulesToTestClasspath

  override def shouldProcessDependenciesRecursively: Boolean =
    false
}

class SbtOrderEnumeratorHandlerFactory extends OrderEnumerationHandler.Factory {
  override def createHandler(module: Module): OrderEnumerationHandler = new SbtOrderEnumeratorHandler

  override def isApplicable(module: Module): Boolean =
    SbtUtil.isSbtModule(module)
}
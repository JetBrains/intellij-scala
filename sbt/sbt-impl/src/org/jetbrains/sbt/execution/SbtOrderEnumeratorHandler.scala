package org.jetbrains.sbt.execution

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.roots.OrderEnumerationHandler.AddDependencyType
import com.intellij.openapi.roots._
import com.intellij.openapi.roots.impl.ModuleOrderEnumerator
import com.intellij.util.CommonProcessors
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.project.module.SbtSourceSetData

import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala

/**
 * ATTENTION: implementation should be in sync with<br>
 * org.jetbrains.jps.incremental.scala.model.JpsSbtDependenciesEnumerationHandler
 */
class SbtOrderEnumeratorHandler(processDependenciesRecursively: Boolean) extends OrderEnumerationHandler {
  override def shouldAddDependency(orderEntry: OrderEntry, settings: OrderEnumeratorSettings): AddDependencyType = {
    (orderEntry, settings) match {
      case (library: LibraryOrderEntry, enumerator: ModuleOrderEnumerator) =>
        val isTransitive = getModuleFromEnumerator(enumerator).fold(false)(_ != library.getOwnerModule)
        if (isTransitive) AddDependencyType.DO_NOT_ADD else AddDependencyType.DEFAULT
      case (moduleOrderEntry: ModuleOrderEntry, enumerator: ModuleOrderEnumerator) if shouldProcessDependenciesRecursively =>
        val moduleFromEnumerator = getModuleFromEnumerator(enumerator)
        moduleFromEnumerator match {
          case Some(enumeratorModule) =>
            // note: when shouldProcessDependenciesRecursively is true, then the project is built with separate modules for prod/test, and
            // we deal with a parent (aka grouping) module. It's set to true for a parent module because we want to process its dependencies recursively,
            // but we don't want to do it indefinitely (as it happens by default). Indefinitely, I mean that when module root (parent module) contains in its dependencies
            // source modules - root.main and root.test, we only want to process their (root.main and root.test) direct dependencies.
            // We don't want to process root.main and root.test dependencies recursively (all the necessary dependencies are already added to them).
            //
            // Without this logic, when e.g., root.main would depend on dummy.main and dummy.main would depend on foo.main with PROVIDED scope,
            // then when compiling root, foo.main would be also compiled, but it shouldn't.
            val ownerModule = moduleOrderEntry.getOwnerModule
            // When ownerModule is the same as enumeratorModule, then we deal with a direct parent modules dependency,
            // and for it, we should leave DEFAULT (it will be processed recursively).
            val isOwnerModuleTheSameAsEnumeratorModule = ownerModule.getName == enumeratorModule.getName
            val isOwnerModuleInEnumeratorModuleDeps = isDependentModule(enumeratorModule, ownerModule)
            if (isOwnerModuleTheSameAsEnumeratorModule || isOwnerModuleInEnumeratorModuleDeps) {
              AddDependencyType.DEFAULT
            } else {
              AddDependencyType.DO_NOT_ADD
            }
          case None => AddDependencyType.DEFAULT
        }
      case _ =>
        AddDependencyType.DEFAULT
    }
  }

  /**
   * Checks whether a parent is a module-dependent on a child
   */
  private def isDependentModule(parent: Module, child: Module): Boolean = {
    val project = parent.getProject
    val dependantModules = ModuleManager.getInstance(project).getModuleDependentModules(child).asScala.toSeq
    dependantModules.contains(parent)
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

  //TODO SCL-22835
  override def shouldAddRuntimeDependenciesToTestCompilationClasspath: Boolean =
    true

  //TODO: sbt doesn't copy resources which are located near main sources to the `target/scala-xy/classes` folder
  //  but looks like simply changing this method return value to `true` doesn't help, investigate...
  override def areResourceFilesFromSourceRootsCopiedToOutput: Boolean =
    super.areResourceFilesFromSourceRootsCopiedToOutput

  override def shouldIncludeTestsFromDependentModulesToTestClasspath: Boolean =
    super.shouldIncludeTestsFromDependentModulesToTestClasspath

  override def shouldProcessDependenciesRecursively: Boolean =
    processDependenciesRecursively
}

class SbtOrderEnumeratorHandlerFactory extends OrderEnumerationHandler.Factory {

  private val RecursiveDependenciesInstance = new SbtOrderEnumeratorHandler(true)
  private val NonRecursiveDependenciesInstance = new SbtOrderEnumeratorHandler(false)

  override def createHandler(module: Module): OrderEnumerationHandler = {
    val recursiveRequired = {
      val separateModulesForProdTest = SbtUtil.isBuiltWithSeparateModulesForProdTest(module.getProject)
      separateModulesForProdTest && !isSbtSourceSetModule(module)
    }

    if (recursiveRequired) RecursiveDependenciesInstance
    else NonRecursiveDependenciesInstance
  }

  override def isApplicable(module: Module): Boolean =
    SbtUtil.isSbtModule(module)

  private def isSbtSourceSetModule(module: Module): Boolean = {
    val moduleDataNode = SbtUtil.getSbtModuleDataNode(module)
    moduleDataNode match {
      case Some(dataNode) =>
        val dataType = dataNode.getKey.getDataType
        dataType == classOf[SbtSourceSetData].getName
      case _ => true
    }
  }
}
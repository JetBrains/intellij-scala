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
      // note: when shouldProcessDependenciesRecursively is true, then the project is built with separate modules for prod/test, and
      // we deal with a parent (aka grouping) module. It's set to true for a parent module because we want to process its dependencies recursively,
      // but we only want to do it to one level of depth. A parent module will always include its own source modules
      // in dependencies - e.g. root.main and root.test, and we only want to process their (root.main and root.test) direct dependencies.
      // We don't want to process root.main and root.test dependencies recursively (all the necessary dependencies are already added to them).
      //
      // It was necessary to implement, because by default, if shouldProcessDependenciesRecursively is true, then dependencies are processed recursively indefinitely.
      case (entry @ (_: ModuleOrderEntry | _: LibraryOrderEntry), enumerator: ModuleOrderEnumerator) if shouldProcessDependenciesRecursively =>
        getAddDependencyType(entry, enumerator)
      case _ =>
        AddDependencyType.DEFAULT
    }
  }

  /**
   * Returns <code>AddDependencyType.DEFAULT</code> if order entry is either a direct dependency of an enumerator module or a dependency of the enumerator module's direct dependencies. <br>
   * Example:
   * <code>
   * <pre> root -> main -> foo -> dummy
   *      -> test</pre>
   *</code>
   * -> means "depends on".
   *
   * In the example above, if we have an enumerator for module <code>root</code>, then for module order entries <code>main</code>, <code>test</code> and <code>foo</code> it returns <code>AddDependencyType.DEFAULT</code>.
   * For module order entry  <code>dummy</code>, it returns <code>AddDependencyTye.DO_NOT_ADD</code>.
   */
  private def getAddDependencyType(orderEntry: OrderEntry, enumerator: ModuleOrderEnumerator): AddDependencyType = {
    val ownerModule = orderEntry.getOwnerModule
    val moduleFromEnumerator = getModuleFromEnumerator(enumerator)
    val shouldAdd = moduleFromEnumerator.forall { enumeratorModule =>
      val isOwnerModuleTheSameAsEnumeratorModule = ownerModule.getName == enumeratorModule.getName
      val isOwnerModuleInEnumeratorModuleDeps = isDependentModule(enumeratorModule, ownerModule)
      isOwnerModuleTheSameAsEnumeratorModule || isOwnerModuleInEnumeratorModuleDeps
    }
    if (shouldAdd) AddDependencyType.DEFAULT
    else AddDependencyType.DO_NOT_ADD
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
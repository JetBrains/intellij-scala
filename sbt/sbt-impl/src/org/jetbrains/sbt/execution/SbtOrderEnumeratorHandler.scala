package org.jetbrains.sbt.execution

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.roots.OrderEnumerationHandler.AddDependencyType
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.ModuleOrderEnumerator
import com.intellij.util.CommonProcessors
import org.jetbrains.sbt.SbtSourceSetUtil.SbtSourceSetModuleExt
import org.jetbrains.sbt.SbtUtil

import java.util

/**
 * ATTENTION!
 *
 * 1. The implementation should be in sync with [[org.jetbrains.jps.incremental.scala.model.JpsSbtDependenciesEnumerationHandler]].
 *
 * 2. Any potential "heavy" operations should be avoided in [[SbtOrderEnumeratorHandler]] and [[SbtOrderEnumeratorHandlerFactory]].
 *    In large projects with many modules and dependencies processed recursively, the methods inside these 2 classes are
 *    executed many, many times. When written in a non-optimized way, this may even block the UI, e.g., in the edit run configuration window.
 *    When introducing any additional logic to these classes, it's best to test the performance in a large project.
 *    More info and an example large project is available in SCL-24366.
 */
class SbtOrderEnumeratorHandler(processDependenciesRecursively: Boolean) extends OrderEnumerationHandler {
  override def shouldAddDependency(orderEntry: OrderEntry, settings: OrderEnumeratorSettings): AddDependencyType =
    (orderEntry, settings) match {
      case (entry @ (_: ModuleOrderEntry | _: LibraryOrderEntry), enumerator: ModuleOrderEnumerator) if shouldProcessDependenciesRecursively =>
        getAddDependencyType(entry, enumerator)
      case _ =>
        AddDependencyType.DEFAULT
    }

  /**
   * Limits recursive dependency processing to two levels.
   *
   * When `shouldProcessDependenciesRecursively` is `true`, then the project is built with main/test modules, and
   * the `SbtOrderEnumeratorHandler` is created for a parent (aka grouping) module. It's set to `true` for parent modules to enable recursive processing,
   * but the goal of this method is to limit this to two levels of depth. For parent modules two levels means:
   *   - its direct source modules (e.g., `root.main` and `root.test`)
   *   - the direct dependencies of those source modules (the direct dependencies of e.g., `root.main` and `root.test`)
   *
   * Without this limit, dependencies would be processed recursively through the entire tree,
   * but source modules like `root.main` and `root.test` already contain all necessary dependencies.
   *
   * @return `AddDependencyType.DEFAULT` if order entry is either a direct dependency of an enumerator module or a dependency of the enumerator module's direct dependencies. <br>
   * Example:
   * <code>
   * <pre> root -> main -> foo -> dummy
   *      -> test</pre>
   *</code>
   * -> means "depends on".
   *
   * In the example above, if we have an enumerator for module `root`, then for module order entries `main`, `test` and `foo` it returns `AddDependencyType.DEFAULT`.
   * For module order entry `dummy`, it returns `AddDependencyTye.DO_NOT_ADD`.
   */
  private def getAddDependencyType(orderEntry: OrderEntry, enumerator: ModuleOrderEnumerator): AddDependencyType = {
    val entryOwnerModule = orderEntry.getOwnerModule
    val moduleFromEnumerator = getModuleFromEnumerator(enumerator)
    val shouldAdd = moduleFromEnumerator.forall { enumeratorModule =>
      val isDirectDependency = entryOwnerModule.getName == enumeratorModule.getName

      isDirectDependency ||
        // Checks whether the enumeratorModule directly depends on the entryOwnerModule (the module that owns the order entry).
        ModuleManager.getInstance(enumeratorModule.getProject).isModuleDependent(enumeratorModule, entryOwnerModule)
    }
    if (shouldAdd) AddDependencyType.DEFAULT
    else AddDependencyType.DO_NOT_ADD
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

/**
 * @see [[SbtOrderEnumeratorHandler]]
 */
class SbtOrderEnumeratorHandlerFactory extends OrderEnumerationHandler.Factory {

  private val RecursiveDependenciesInstance = new SbtOrderEnumeratorHandler(true)
  private val NonRecursiveDependenciesInstance = new SbtOrderEnumeratorHandler(false)

  override def createHandler(module: Module): OrderEnumerationHandler = {
    val recursiveRequired = {
      val separateModulesForProdTest = SbtUtil.hasScalaCompilerSeparateProdTestSourcesEnabled(module.getProject)
      separateModulesForProdTest && !module.isSbtSourceSetModule
    }

    if (recursiveRequired) RecursiveDependenciesInstance
    else NonRecursiveDependenciesInstance
  }

  override def isApplicable(module: Module): Boolean =
    SbtUtil.isSbtModule(module)
}
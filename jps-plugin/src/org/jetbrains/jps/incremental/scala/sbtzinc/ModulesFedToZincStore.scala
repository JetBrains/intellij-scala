package org.jetbrains.jps.incremental.scala.sbtzinc

import java.util

import com.intellij.openapi.util.Key
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.scala.SourceDependenciesProviderService
import org.jetbrains.jps.model.module.{JpsDependencyElement, JpsModule, JpsModuleDependency, JpsModuleSourceDependency}

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
  * Keep track of dirty modules and dirty dependant modules for a single build
  */
object ModulesFedToZincStore {
  // We need to duplicate storage since context data somehow miss recompiled modules/scopes
  private val dataKey: Key[Set[String]] = new Key[Set[String]]("MODULES_FED_TO_ZINC") {}
  private val modulesFedToZincStore = java.util.concurrent.ConcurrentHashMap.newKeySet[String]()

  def checkIfAnyModuleDependencyWasFedToZinc(context: CompileContext, chunk: ModuleChunk): Boolean = {
    val modulesFedToZinc = ModulesFedToZincStore.get(context)

    def wasCompiledBefore(name: String) =
      modulesFedToZinc.contains(name) || modulesFedToZincStore.contains(name)

    def isModuleAndWasCompiledBefore(elem: JpsDependencyElement): Boolean = {
      elem match {
        case moduleDep: JpsModuleDependency =>
          wasCompiledBefore(moduleDep.getModule.getName)
        case   _: JpsModuleSourceDependency =>
          wasCompiledBefore(elem.getContainingModule.getName)
        case _ => false
      }
    }

    def hasDependencyCompiledBefore(module: JpsModule) =
      module.getDependenciesList.getDependencies.iterator().asScala.exists(isModuleAndWasCompiledBefore)

    val wasFedToZinc = chunk.getModules.iterator().asScala.exists(hasDependencyCompiledBefore)

    wasFedToZinc
  }

  def add(context: CompileContext, moduleNames: Seq[String]): Unit = {
    dataKey.synchronized {
      val previous = getValue(context)
      val next = previous ++ moduleNames
      context.putUserData(dataKey, next)
      modulesFedToZincStore.addAll(moduleNames.asJava)
    }
  }

  private def get(context: CompileContext): Set[String] =
    dataKey.synchronized(getValue(context))

  private def getValue(context: CompileContext): Set[String] =
    Option(context.getUserData(dataKey)).getOrElse(Set.empty[String])

}

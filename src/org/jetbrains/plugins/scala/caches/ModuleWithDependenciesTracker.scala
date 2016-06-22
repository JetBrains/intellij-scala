package org.jetbrains.plugins.scala.caches

import com.intellij.ProjectTopics
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.{ModuleAdapter, Project}
import com.intellij.openapi.roots.{ModuleRootEvent, ModuleRootListener}
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.impl.PsiModificationTrackerImpl
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

import scala.collection.mutable

/**
  * @author Nikolay.Tropin
  */
class ModuleWithDependenciesTracker(module: Module) extends ScalaSmartModificationTracker {

  override def parentTracker: Option[ModificationTracker] = Some(LibraryModificationTracker.instance(module.getProject))

  //including the module itself
  private var dependentModules: Set[Module] = collectDependentModules

  private def collectDependentModules = {
    val moduleGraph = ModuleManager.getInstance(module.getProject).moduleGraph()
    val result = mutable.Set.empty[Module]
    val seenModules = mutable.Set.empty[Module]

    def addAllDependents(m: Module): Unit = {
      seenModules.add(m)
      val iterator = moduleGraph.getOut(m)
      while (iterator.hasNext) {
        val next = iterator.next
        result.add(next)
        if (!seenModules.contains(next)) addAllDependents(next)
      }
    }
    result.add(module)
    addAllDependents(module)

    result.toSet
  }

  override def onPsiChange(): Unit = {
    dependentModules.map(ModuleWithDependenciesTracker.instance).foreach(_.incModCounter())

    updateJavaStructureCounter()
  }

  def updateDependentModules(): Unit = {
    dependentModules = collectDependentModules
  }

  private def updateJavaStructureCounter() = {
    PsiModificationTracker.SERVICE.getInstance(module.getProject) match {
      case impl: PsiModificationTrackerImpl => impl.incCounter()
      case _ =>
    }
  }
}

object ModuleWithDependenciesTracker {
  def registerListeners(project: Project) = {
    val busConnection = project.getMessageBus.connect(project)

    def updateAll(): Unit = {
      ScalaPsiManager.instance(project).clearModuleTrackers()
      val moduleTrackers = ModuleManager.getInstance(project).getModules.map(ModuleWithDependenciesTracker.instance)
      for (modTracker <- moduleTrackers) {
        modTracker.updateDependentModules()
        modTracker.incModCounter()
      }
    }

    busConnection.subscribe(ProjectTopics.MODULES, new ModuleAdapter {
      override def moduleAdded(project: Project, module: Module): Unit = updateAll()

      override def moduleRemoved(project: Project, module: Module): Unit = updateAll()
    })

    busConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener {
      override def rootsChanged(event: ModuleRootEvent): Unit = updateAll()
    })
  }

  def instance(module: Module) = ScalaPsiManager.instance(module.getProject).moduleWithDependenciesTracker(module)
}
package org.jetbrains.plugins.scala.caches

import java.util.concurrent.atomic.AtomicLong

import com.intellij.ProjectTopics
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.{DumbService, ModuleAdapter, Project}
import com.intellij.openapi.roots.{ModuleRootEvent, ModuleRootListener, ProjectRootManager}
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi._
import com.intellij.psi.impl.{PsiModificationTrackerImpl, PsiTreeChangeEventImpl}
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScModificationTrackerOwner
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

import scala.collection.mutable

/**
  * @author Nikolay.Tropin
  */

sealed trait ScalaSmartModificationTracker extends ModificationTracker {

  protected val counter = new AtomicLong(0L)

  def parentTracker: Option[ModificationTracker] = None

  override def getModificationCount: Long = parentTracker.map(_.getModificationCount).getOrElse(0L) + counter.get()

  def incModCounter(): Unit = counter.incrementAndGet()
}

object ScalaSmartModificationTracker {
  val EVER_CHANGED = new ScalaSmartModificationTracker {
    override def getModificationCount: Long = ModificationTracker.EVER_CHANGED.getModificationCount
  }

  val NEVER_CHANGED = new ScalaSmartModificationTracker {
    override def getModificationCount: Long = ModificationTracker.NEVER_CHANGED.getModificationCount
  }

  def javaOutOfCodeBlockTracker(project: Project) = new ScalaSmartModificationTracker {
    override def getModificationCount: Long = PsiManager.getInstance(project).getModificationTracker.getOutOfCodeBlockModificationCount
  }

  def forLibraries(project: Project) = ScalaPsiManager.instance(project).libraryModificationTracker

  def forModule(module: Module) = ScalaPsiManager.instance(module.getProject).moduleWithDependenciesTracker(module)

  def updateModificationCount(elem: PsiElement): Unit = {
    CachesUtil.enclosingModificationOwner(elem) match {
      case loc: LocalModificationTracker => loc.incModCounter()
      case mod: ModuleWithDependenciesTracker if elem.isPhysical =>
        val modifiableCodeBlock = elem.contexts.collectFirst {
          case b: PsiModifiableCodeBlock => b
        }
        if (modifiableCodeBlock.isEmpty || modifiableCodeBlock.get.shouldChangeModificationCount(elem)) {
          mod.incModCounter()
        }
      case _ =>
    }
  }

  def registerListeners(project: Project) = {
    PsiManager.getInstance(project).addPsiTreeChangeListener(CacheInvalidator, project)

    val busConnection = project.getMessageBus.connect(project)

    def updateAllModuleTrackers(): Unit = {
      ScalaPsiManager.instance(project).clearModuleTrackers()
      val moduleTrackers = ModuleManager.getInstance(project).getModules.map(ScalaSmartModificationTracker.forModule)
      for (modTracker <- moduleTrackers) {
        modTracker.updateDependentModules()
        modTracker.incModCounter()
      }
    }

    busConnection.subscribe(ProjectTopics.MODULES, new ModuleAdapter {
      override def moduleAdded(project: Project, module: Module): Unit = updateAllModuleTrackers()

      override def moduleRemoved(project: Project, module: Module): Unit = updateAllModuleTrackers()
    })

    busConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener {
      override def rootsChanged(event: ModuleRootEvent): Unit = updateAllModuleTrackers()
    })

    busConnection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      def enteredDumbMode(): Unit = forLibraries(project).incModCounter()

      def exitDumbMode(): Unit = forLibraries(project).incModCounter()
    })
  }

  object CacheInvalidator extends PsiTreeChangeAdapter {
    private def isGeneric(event: PsiTreeChangeEvent) = event match {
      case impl: PsiTreeChangeEventImpl => impl.isGenericChange
      case _ => false
    }

    override def childRemoved(event: PsiTreeChangeEvent): Unit = {
      updateModificationCount(event.getParent)
    }

    override def childReplaced(event: PsiTreeChangeEvent): Unit = {
      updateModificationCount(event.getParent)
    }

    override def childAdded(event: PsiTreeChangeEvent): Unit = {
      updateModificationCount(event.getParent)
    }

    override def childrenChanged(event: PsiTreeChangeEvent): Unit = {
      if (!isGeneric(event))
        updateModificationCount(event.getParent)
    }

    override def childMoved(event: PsiTreeChangeEvent): Unit = {
      updateModificationCount(event.getParent)
    }

    override def propertyChanged(event: PsiTreeChangeEvent): Unit = {
      updateModificationCount(event.getElement)
    }
  }
}

class LibraryModificationTracker(project: Project) extends ScalaSmartModificationTracker {
  override def parentTracker: Option[ModificationTracker] = Option(ProjectRootManager.getInstance(project))

  project.getMessageBus.connect(project).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
    def enteredDumbMode(): Unit = LibraryModificationTracker.this.incModCounter()

    def exitDumbMode(): Unit = LibraryModificationTracker.this.incModCounter()
  })
}

class ModuleWithDependenciesTracker(module: Module) extends ScalaSmartModificationTracker {

  override def parentTracker: Option[ModificationTracker] = Some(ScalaSmartModificationTracker.forLibraries(module.getProject))

  //including the module itself
  @volatile private var dependentModules: Set[Module] = collectDependentModules

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

  override def incModCounter(): Unit = {
    dependentModules.map(ScalaSmartModificationTracker.forModule).foreach(_.counter.incrementAndGet())

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

class LocalModificationTracker(owner: ScModificationTrackerOwner) extends ScalaSmartModificationTracker {
  override def parentTracker: Option[ModificationTracker] = {
    Some(CachesUtil.enclosingModificationOwner(owner.getContext))
  }
}






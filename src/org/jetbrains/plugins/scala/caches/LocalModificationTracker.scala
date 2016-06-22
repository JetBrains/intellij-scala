package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScModificationTrackerOwner
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

/**
  * @author Nikolay.Tropin
  */
class LocalModificationTracker(owner: ScModificationTrackerOwner) extends ScalaSmartModificationTracker {

  override def parentTracker: Option[ModificationTracker] = {
    if (!owner.isValid) return Some(ScalaSmartModificationTracker.EVER_CHANGED)

    val containingOwnerTracker: Option[ModificationTracker] = owner.contexts.collectFirst {
      case o: ScModificationTrackerOwner if o.isValidModificationTrackerOwner => o.getModificationTracker
    }
    containingOwnerTracker.orElse {
      owner.module.map(ModuleWithDependenciesTracker.instance)
    }.orElse {
      Some(ScalaSmartModificationTracker.EVER_CHANGED)
    }
  }


  override def getModificationCount: Long = {
    super.getModificationCount
  }

  override def onPsiChange(): Unit = incModCounter()
}

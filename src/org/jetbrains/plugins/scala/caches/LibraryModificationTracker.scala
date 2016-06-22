package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

/**
  * @author Nikolay.Tropin
  */
class LibraryModificationTracker(project: Project) extends ScalaSmartModificationTracker {
  override def parentTracker: Option[ModificationTracker] = Option(ProjectRootManager.getInstance(project))

  project.getMessageBus.connect(project).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
    def enteredDumbMode(): Unit = LibraryModificationTracker.this.incModCounter()

    def exitDumbMode(): Unit = LibraryModificationTracker.this.incModCounter()
  })
}

object LibraryModificationTracker {
  def instance(project: Project) = ScalaPsiManager.instance(project).libraryModificationTracker
}
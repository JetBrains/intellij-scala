package org.jetbrains.plugins.scala.caches.stats

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.caches.ModTracker

import java.util

final class ScalaModificationTrackersDataSource(project: Project) extends DataSource[ModificationTrackersData] {
  override def isActive: Boolean = Tracer.isEnabled

  override def stop(): Unit = Tracer.setEnabled(false)

  override def resume(): Unit = Tracer.setEnabled(true)

  override def clear(): Unit = ()

  override def getCurrentData: util.List[ModificationTrackersData] = {
    val result = new util.ArrayList[ModificationTrackersData]()

    result.add(ModificationTrackersData(
      "ModTracker.anyScalaPsiChange",
      ModTracker.anyScalaPsiChange.getModificationCount
    ))
    result.add(ModificationTrackersData(
      "ModTracker.physicalPsiChange",
      ModTracker.physicalPsiChange(project).getModificationCount
    ))

    result
  }
}

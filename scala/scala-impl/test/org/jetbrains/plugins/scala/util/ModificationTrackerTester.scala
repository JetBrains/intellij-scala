package org.jetbrains.plugins.scala.util

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.caches.ModTracker
import org.junit.Assert.assertEquals

final class ModificationTrackerTester(project: Project) {
  val modCountAnyBefore: Long = modCountAny
  val modCountPhysicalBefore: Long = modCountPhysical

  def modCountAny: Long = ModTracker.anyScalaPsiChange.getModificationCount

  def modCountPhysical: Long = ModTracker.physicalPsiChange(project).getModificationCount

  def assertPsiModificationCountNotChanged(actionName: String): Unit = {
    assertAnyPsiModificationCountNotChanged(actionName)
    assertPhysicalPsiModificationCountNotChanged(actionName)
  }

  private def assertAnyPsiModificationCountNotChanged(actionName: String): Unit = {
    assertEquals(s"ModTracker.anyScalaPsiChange modification count has changed after $actionName", modCountAnyBefore, modCountAny)
  }

  private def assertPhysicalPsiModificationCountNotChanged(actionName: String): Unit = {
    assertEquals(s"ModTracker.physicalPsiChange modification count has changed after $actionName", modCountPhysicalBefore, modCountPhysical)
  }
}
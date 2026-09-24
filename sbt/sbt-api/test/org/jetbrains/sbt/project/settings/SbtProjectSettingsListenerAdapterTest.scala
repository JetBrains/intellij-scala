package org.jetbrains.sbt.project.settings

import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener
import org.junit.Assert.{assertEquals, assertSame}
import org.junit.Test

import java.util
import scala.collection.mutable.ArrayBuffer

class SbtProjectSettingsListenerAdapterTest {
  @Test
  def delegatesAllSettingsEvents(): Unit = {
    val events = ArrayBuffer.empty[String]
    val settings = new util.ArrayList[SbtProjectSettings]()
    val paths = util.Collections.singleton("/project")
    val delegate = new ExternalSystemSettingsListener[SbtProjectSettings] {
      override def onProjectRenamed(oldName: String, newName: String): Unit = {
        assertEquals("old", oldName)
        assertEquals("new", newName)
        events += "renamed"
      }

      override def onProjectsLoaded(actual: util.Collection[SbtProjectSettings]): Unit = {
        assertSame(settings, actual)
        events += "loaded"
      }

      override def onProjectsLinked(actual: util.Collection[SbtProjectSettings]): Unit = {
        assertSame(settings, actual)
        events += "linked"
      }

      override def onProjectsUnlinked(actual: util.Set[String]): Unit = {
        assertSame(paths, actual)
        events += "unlinked"
      }

      override def onBulkChangeStart(): Unit = { events += "start" }
      override def onBulkChangeEnd(): Unit = { events += "end" }
    }
    val adapter: SbtProjectSettingsListener = new SbtProjectSettingsListenerAdapter(delegate)

    adapter.onBulkChangeStart()
    adapter.onProjectRenamed("old", "new")
    adapter.onProjectsLoaded(settings)
    adapter.onProjectsLinked(settings)
    adapter.onProjectsUnlinked(paths)
    adapter.onBulkChangeEnd()

    assertEquals(Seq("start", "renamed", "loaded", "linked", "unlinked", "end"), events.toSeq)
  }
}

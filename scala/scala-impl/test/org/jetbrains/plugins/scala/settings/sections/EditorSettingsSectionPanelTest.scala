package org.jetbrains.plugins.scala.settings.sections

import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.{FixturesKt, TestFixture}
import org.jetbrains.plugins.scala.settings.sections.EditorSettingsSectionPanelTest.projectFixture
import org.junit.jupiter.api.Assertions.{assertFalse, assertTrue}
import org.junit.jupiter.api.Test

import javax.swing.JPanel
import scala.annotation.static

@TestApplication
class EditorSettingsSectionPanelTest {

  @Test
  def defaultProjectShowsBothHighlightingModeSelectors(): Unit = {
    val panel = new EditorSettingsSectionPanel(ProjectManager.getInstance().getDefaultProject)

    assertTrue(fieldValue(panel, "typeCheckerScala2Section").isVisible)
    assertTrue(fieldValue(panel, "typeCheckerScala3Section").isVisible)
  }

  @Test
  def scalaLessProjectHidesBothHighlightingModeSelectors(): Unit = {
    val panel = new EditorSettingsSectionPanel(projectFixture.get())

    assertFalse(fieldValue(panel, "typeCheckerScala2Section").isVisible)
    assertFalse(fieldValue(panel, "typeCheckerScala3Section").isVisible)
  }

  private def fieldValue(panel: EditorSettingsSectionPanel, fieldName: String): JPanel = {
    val field = classOf[EditorSettingsSectionPanel].getDeclaredField(fieldName)
    field.setAccessible(true)
    field.get(panel).asInstanceOf[JPanel]
  }
}

private object EditorSettingsSectionPanelTest {
  @static val projectFixture: TestFixture[Project] = FixturesKt.projectFixture()
}

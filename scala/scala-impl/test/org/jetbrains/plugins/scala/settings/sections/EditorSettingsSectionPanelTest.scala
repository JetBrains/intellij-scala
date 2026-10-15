package org.jetbrains.plugins.scala.settings.sections

import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Assert.{assertFalse, assertTrue}

class EditorSettingsSectionPanelTest extends LightPlatformTestCase {

  def testDefaultProjectShowsBothHighlightingModeSelectors(): Unit = {
    val panel = new EditorSettingsSectionPanel(ProjectManager.getInstance().getDefaultProject)

    assertTrue(fieldValue(panel, "typeCheckerScala2Section").isVisible)
    assertTrue(fieldValue(panel, "typeCheckerScala3Section").isVisible)
  }

  def testScalaLessProjectHidesBothHighlightingModeSelectors(): Unit = {
    val panel = new EditorSettingsSectionPanel(getProject)

    assertFalse(fieldValue(panel, "typeCheckerScala2Section").isVisible)
    assertFalse(fieldValue(panel, "typeCheckerScala3Section").isVisible)
  }

  private def fieldValue(panel: EditorSettingsSectionPanel, fieldName: String) = {
    val field = classOf[EditorSettingsSectionPanel].getDeclaredField(fieldName)
    field.setAccessible(true)
    field.get(panel).asInstanceOf[javax.swing.JPanel]
  }
}

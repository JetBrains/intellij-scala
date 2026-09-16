package org.jetbrains.plugins.scala.settings

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.junit.Assert.assertFalse
import org.junit.Test

import scala.jdk.CollectionConverters._

class ScalaProjectSettingsTest {

  // SCL-25613, SCL-25932
  /**
   * Although Ammonite support was dropped, migrating a workspace created by an
   * older plugin version must not cause an exception.
   *
   * This low-priority compatibility test may be removed in 2027.3.
   */
  @Test
  def legacyScFileModesAreIgnored(): Unit = {
    Seq("Ammonite", "Auto").foreach(assertLegacyScFileModeIsIgnored)
  }

  private def assertLegacyScFileModeIsIgnored(legacyMode: String): Unit = {
    val settings = deserializeSettingsWithLegacyScFileMode(legacyMode)
    val savedState = serializeSettings(settings)

    assertFalse(
      s"Legacy $legacyMode mode was persisted again",
      savedState.getChildren("option").asScala.exists(_.getAttributeValue("name") == "SC_FILE_MODE")
    )
  }

  private def deserializeSettingsWithLegacyScFileMode(legacyMode: String): ScalaProjectSettings = {
    val legacyState = JDOMUtil.load(
      s"""<ScalaProjectSettings>
         |  <option name="SC_FILE_MODE" value="$legacyMode" />
         |</ScalaProjectSettings>""".stripMargin
    )
    val settings = new ScalaProjectSettings()
    // Loading a workspace with the obsolete option must not throw.
    XmlSerializer.deserializeInto(settings, legacyState)
    settings
  }

  private def serializeSettings(settings: ScalaProjectSettings): Element = {
    val savedState = new Element("ScalaProjectSettings")
    XmlSerializer.serializeInto(settings, savedState)
    savedState
  }
}

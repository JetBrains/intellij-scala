package org.jetbrains.plugins.scala.lang.formatting.settings

;

import com.intellij.openapi.util.JDOMUtil
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jdom.Element
import org.junit.Assert._

class ScalaCodeStyleSettingsTest extends LightJavaCodeInsightFixtureTestCase {

  def testReadLegacyMultilineStringSettingsNames(): Unit = {
    val settings = new ScalaCodeStyleSettings(CodeStyleSettings.getDefaults)

    val input =
      """<code_scheme name="Project" version="173">
        |  <ScalaCodeStyleSettings>
        |    <option name="MARGIN_CHAR" value="#" />
        |    <option name="MULTI_LINE_STRING_MARGIN_INDENT" value="3" />
        |    <option name="MULTI_LINE_QUOTES_ON_NEW_LINE" value="false" />
        |    <option name="PROCESS_MARGIN_ON_COPY_PASTE" value="false" />
        |  </ScalaCodeStyleSettings>
        |</code_scheme>
        |""".stripMargin
    val element: Element = JDOMUtil.load(input)

    settings.readExternal(element)

    assertEquals("#", settings.MULTILINE_STRING_MARGIN_CHAR)
    assertEquals(3, settings.MULTILINE_STRING_MARGIN_INDENT)
    assertFalse(settings.MULTILINE_STRING_OPENING_QUOTES_ON_NEW_LINE)
    assertFalse(settings.MULTILINE_STRING_PROCESS_MARGIN_ON_COPY_PASTE)
  }

}
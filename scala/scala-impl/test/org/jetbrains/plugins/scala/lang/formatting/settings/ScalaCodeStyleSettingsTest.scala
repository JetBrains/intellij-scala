package org.jetbrains.plugins.scala.lang.formatting.settings

;

import junit.framework.TestCase
import org.jdom.{Element, JDOMException, JDOMFactory}
import java.io.IOException
import java.io.StringReader

import org.jdom.input.SAXBuilder
import org.junit.Assert._

class ScalaCodeStyleSettingsTest extends TestCase {

  def testReadLegacyMultilineStringSettingsNames(): Unit = {
    val settings = new ScalaCodeStyleSettings()

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
    val element: Element = {
      val saxBuilder = new SAXBuilder()
      val document = saxBuilder.build(new StringReader(input))
      document.getRootElement
    }

    settings.readExternal(element)

    assertEquals("#", settings.MULTILINE_STRING_MARGIN_CHAR)
    assertEquals(3, settings.MULTILINE_STRING_MARGIN_INDENT)
    assertFalse(settings.MULTILINE_STRING_OPENING_QUOTES_ON_NEW_LINE)
    assertFalse(settings.MULTILINE_STRING_PROCESS_MARGIN_ON_COPY_PASTE)
  }

}
package org.jetbrains.plugins.hocon.formatting

import com.intellij.openapi.util.JDOMUtil
import com.intellij.psi.codeStyle.{CodeStyleManager, CodeStyleSettingsManager}
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.plugins.hocon.CommonUtil.TextRange
import org.jetbrains.plugins.hocon.{HoconFileSetTestCase, TestSuiteCompanion}
import org.junit.runner.RunWith
import org.junit.runners.AllTests

object HoconFormatterTest extends TestSuiteCompanion[HoconFormatterTest]

@RunWith(classOf[AllTests])
class HoconFormatterTest extends HoconFileSetTestCase("formatter") {

  import HoconFileSetTestCase._
  import LightPlatformTestCase.getProject

  protected def transform(data: Seq[String]): String = {
    val Seq(settingsXml, input) = data

    val settings = CodeStyleSettingsManager.getSettings(getProject)
    settings.readExternal(JDOMUtil.load(settingsXml))

    val psiFile = createPseudoPhysicalHoconFile(input)

    inWriteCommandAction {
      val TextRange(start, end) = psiFile.getTextRange
      CodeStyleManager.getInstance(getProject).reformatText(psiFile, start, end)
    }

    psiFile.getText
  }
}

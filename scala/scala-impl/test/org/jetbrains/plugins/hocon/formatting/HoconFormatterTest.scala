package org.jetbrains.plugins.hocon
package formatting

import com.intellij.openapi.util.JDOMUtil
import com.intellij.psi.codeStyle.{CodeStyleManager, CodeStyleSettingsManager}
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.plugins.hocon.CommonUtil.TextRange
import org.junit.runner.RunWith
import org.junit.runners.AllTests

@RunWith(classOf[AllTests])
class HoconFormatterTest extends HoconFileSetTestCase("formatter") {

  import HoconFileSetTestCase._
  import LightPlatformTestCase.getProject

  override protected def transform(data: Seq[String]): String = {
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

object HoconFormatterTest extends TestSuiteCompanion[HoconFormatterTest]

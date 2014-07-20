package intellijhocon
package formatting

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.codeStyle.{CodeStyleManager, CodeStyleSettingsManager, CommonCodeStyleSettings}
import intellijhocon.HoconTestUtils._
import intellijhocon.Util.TextRange
import intellijhocon.codestyle.HoconCustomCodeStyleSettings
import intellijhocon.lang.HoconLanguage
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase
import org.jetbrains.plugins.scala.util.TestUtils

class HoconFormatterTest extends BaseScalaFileSetTestCase(TestUtils.getTestDataPath + "/hocon/formatter/data") {
  private def adjustSettings(testName: String) {
    val settings = CodeStyleSettingsManager.getSettings(getProject)
    val commonSettings = settings.getCommonSettings(HoconLanguage)
    val customSettings = settings.getCustomSettings(classOf[HoconCustomCodeStyleSettings])
    settingsPerTest.get(testName).foreach(_.apply(commonSettings, customSettings))
  }

  def transform(testName: String, data: Array[String]) = {
    adjustSettings(testName)
    val psiFile = createPseudoPhysicalHoconFile(getProject, data(0))

    def reformatAction() = ApplicationManager.getApplication.runWriteAction(try {
      val TextRange(start, end) = psiFile.getTextRange
      CodeStyleManager.getInstance(getProject).reformatText(psiFile, start, end)
    } catch {
      case e: Exception => e.printStackTrace()
    })

    CommandProcessor.getInstance.executeCommand(getProject, reformatAction(), null, null)

    psiFile.getText
  }

  private val settingsPerTest = Map[String, (CommonCodeStyleSettings, HoconCustomCodeStyleSettings) => Unit](
    "indentation" -> {
      case (commonSettings, customSettings) =>
        val hoconIndentOptions = commonSettings.getIndentOptions
        hoconIndentOptions.INDENT_SIZE = 2
        hoconIndentOptions.CONTINUATION_INDENT_SIZE = 1 // non-standard, to see the difference from INDENT_SIZE
        hoconIndentOptions.TAB_SIZE = 2
    },

    "noSpaceAfterAssignment" -> {
      case (commonSettings, customSettings) =>
        customSettings.SPACE_AFTER_ASSIGNMENT = false
    },
    "spaceAfterAssignment" -> {
      case (commonSettings, customSettings) =>
        customSettings.SPACE_AFTER_ASSIGNMENT = true
    },
    "noSpaceBeforeAssignment" -> {
      case (commonSettings, customSettings) =>
        customSettings.SPACE_BEFORE_ASSIGNMENT = false
    },
    "spaceBeforeAssignment" -> {
      case (commonSettings, customSettings) =>
        customSettings.SPACE_BEFORE_ASSIGNMENT = true
    },
    "noSpaceAfterColon" -> {
      case (commonSettings, customSettings) =>
        customSettings.SPACE_AFTER_COLON = false
    },
    "spaceAfterColon" -> {
      case (commonSettings, customSettings) =>
        customSettings.SPACE_AFTER_COLON = true
    },
    "noSpaceBeforeColon" -> {
      case (commonSettings, customSettings) =>
        customSettings.SPACE_BEFORE_COLON = false
    },
    "spaceBeforeColon" -> {
      case (commonSettings, customSettings) =>
        customSettings.SPACE_BEFORE_COLON = true
    },
    "noSpaceAfterComma" -> {
      case (commonSettings, customSettings) =>
        commonSettings.SPACE_AFTER_COMMA = false
    },
    "spaceAfterComma" -> {
      case (commonSettings, customSettings) =>
        commonSettings.SPACE_AFTER_COMMA = true
    },
    "noSpaceBeforeComma" -> {
      case (commonSettings, customSettings) =>
        commonSettings.SPACE_BEFORE_COMMA = false
    },
    "spaceBeforeComma" -> {
      case (commonSettings, customSettings) =>
        commonSettings.SPACE_BEFORE_COMMA = true
    },
    "noSpaceAfterQmark" -> {
      case (commonSettings, customSettings) =>
        customSettings.SPACE_AFTER_QMARK = false
    },
    "spaceAfterQmark" -> {
      case (commonSettings, customSettings) =>
        customSettings.SPACE_AFTER_QMARK = true
    },
    "noSpaceWithinObjectBraces" -> {
      case (commonSettings, customSettings) =>
        commonSettings.SPACE_WITHIN_BRACES = false
    },
    "spaceWithinObjectBraces" -> {
      case (commonSettings, customSettings) =>
        commonSettings.SPACE_WITHIN_BRACES = true
    },
    "noSpaceWithinBrackets" -> {
      case (commonSettings, customSettings) =>
        commonSettings.SPACE_WITHIN_BRACKETS = false
    },
    "spaceWithinBrackets" -> {
      case (commonSettings, customSettings) =>
        commonSettings.SPACE_WITHIN_BRACKETS = true
    },
    "noSpaceWithinIncludeQualifierParens" -> {
      case (commonSettings, customSettings) =>
        commonSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = false
    },
    "spaceWithinIncludeQualifierParens" -> {
      case (commonSettings, customSettings) =>
        commonSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true
    },
    "noSpaceWithinSubstitutionBraces" -> {
      case (commonSettings, customSettings) =>
        customSettings.SPACES_WITHIN_SUBSTITUTION_BRACES = false
    },
    "spaceWithinSubstitutionBraces" -> {
      case (commonSettings, customSettings) =>
        customSettings.SPACES_WITHIN_SUBSTITUTION_BRACES = true
    },
    "noSpaceAfterPathBeforeLbrace" -> {
      case (commonSettings, customSettings) =>
        customSettings.SPACE_BEFORE_LBRACE_AFTER_PATH = false
    },
    "spaceAfterPathBeforeLbrace" -> {
      case (commonSettings, customSettings) =>
        customSettings.SPACE_BEFORE_LBRACE_AFTER_PATH = true
    }
  )
}

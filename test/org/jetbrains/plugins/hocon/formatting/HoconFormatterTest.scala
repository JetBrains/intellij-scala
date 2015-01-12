package org.jetbrains.plugins.hocon.formatting

import java.io.File

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.codeStyle.{CodeStyleManager, CodeStyleSettingsManager}
import org.jetbrains.plugins.hocon.CommonUtil.TextRange
import org.jetbrains.plugins.scala.testcases.ScalaFileSetTestCase
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert

class HoconFormatterTest extends ScalaFileSetTestCase(TestUtils.getTestDataPath + "/hocon/formatter/data") {

  protected def runTest(file: File) = {
    import org.jetbrains.plugins.hocon.HoconTestUtils._

    val fileContents = new String(FileUtil.loadFileText(file, "UTF-8")).replaceAllLiterally("\r", "")
    val Array(settingsXml, input, expectedResult) = fileContents.split("-{5,}", 3).map(_.trim)

    val settings = CodeStyleSettingsManager.getSettings(getProject)
    settings.readExternal(JDOMUtil.loadDocument(settingsXml).getRootElement)

    val psiFile = createPseudoPhysicalHoconFile(getProject, input)

    def reformatAction() = ApplicationManager.getApplication.runWriteAction(try {
      val TextRange(start, end) = psiFile.getTextRange
      CodeStyleManager.getInstance(getProject).reformatText(psiFile, start, end)
    } catch {
      case e: Exception => e.printStackTrace()
    })

    CommandProcessor.getInstance.executeCommand(getProject, reformatAction(), null, null)

    Assert.assertEquals(expectedResult, psiFile.getText.replaceAllLiterally("\r", ""))
  }

}

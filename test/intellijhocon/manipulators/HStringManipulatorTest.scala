package intellijhocon
package manipulators

import java.io.File

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.{WriteCommandAction, CommandProcessor}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.{PsiDocumentManager, ElementManipulators}
import org.jetbrains.plugins.scala.testcases.ScalaFileSetTestCase
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert
import psi.HString

class HStringManipulatorTest extends ScalaFileSetTestCase(TestUtils.getTestDataPath + "/hocon/stringManipulator/data") {

  protected def runTest(file: File) = {
    import intellijhocon.HoconTestUtils._

    val fileContents = new String(FileUtil.loadFileText(file, "UTF-8")).replaceAllLiterally("\r", "")
    val Array(input, newContentInBrackets, expectedResult) = fileContents.split("-{5,}", 3).map(_.trim)
    val newContent = newContentInBrackets.stripPrefix("[").stripSuffix("]")

    val psiFile = createPseudoPhysicalHoconFile(getProject, input)

    def editAction() = ApplicationManager.getApplication.runWriteAction(try {
      val string = psiFile.toplevelEntries.fields.toList.head.value.get.asInstanceOf[HString]
      val manipulator = ElementManipulators.getManipulator(string)
      val range = manipulator.getRangeInElement(string)
      manipulator.handleContentChange(string, range, newContent)
    } catch {
      case e: Exception => e.printStackTrace()
    })

    CommandProcessor.getInstance.executeCommand(getProject, editAction(), null, null)

    Assert.assertEquals(expectedResult, psiFile.getText.replaceAllLiterally("\r", ""))
  }
}

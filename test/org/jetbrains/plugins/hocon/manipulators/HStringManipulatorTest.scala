package org.jetbrains.plugins.hocon.manipulators

import java.io.File

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.ElementManipulators
import org.jetbrains.plugins.hocon.TestSuiteCompanion
import org.jetbrains.plugins.hocon.psi.HString
import org.jetbrains.plugins.scala.testcases.ScalaFileSetTestCase
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert
import org.junit.runner.RunWith
import org.junit.runners.AllTests

object HStringManipulatorTest extends TestSuiteCompanion[HStringManipulatorTest]

@RunWith(classOf[AllTests])
class HStringManipulatorTest extends ScalaFileSetTestCase(TestUtils.getTestDataPath + "/hocon/stringManipulator/data") {

  protected def runTest(file: File) = {
    import org.jetbrains.plugins.hocon.HoconTestUtils._

    val fileContents = new String(FileUtil.loadFileText(file, "UTF-8")).replaceAllLiterally("\r", "")
    val Array(input, positionAndNewContents, expectedResult) = fileContents.split("-{5,}", 3).map(_.trim)
    val Array(positionStr, newContentsInBrackets) = positionAndNewContents.split(",")
    val position = positionStr.toInt
    val newContent = newContentsInBrackets.stripPrefix("[").stripSuffix("]")

    val psiFile = createPseudoPhysicalHoconFile(getProject, input)

    def editAction() = ApplicationManager.getApplication.runWriteAction(try {
      val string = Iterator.iterate(psiFile.findElementAt(position))(_.getParent)
        .collectFirst({ case hs: HString => hs }).get
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

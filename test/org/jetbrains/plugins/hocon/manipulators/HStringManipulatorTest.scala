package org.jetbrains.plugins.hocon.manipulators

import com.intellij.psi.ElementManipulators
import org.jetbrains.plugins.hocon.HoconTestUtils._
import org.jetbrains.plugins.hocon.psi.HString
import org.jetbrains.plugins.hocon.{HoconFileSetTestCase, TestSuiteCompanion}
import org.junit.runner.RunWith
import org.junit.runners.AllTests

object HStringManipulatorTest extends TestSuiteCompanion[HStringManipulatorTest]

@RunWith(classOf[AllTests])
class HStringManipulatorTest extends HoconFileSetTestCase("stringManipulator") {
  protected def transform(data: Seq[String]): String = {
    val Seq(inputCaret, newContentInBrackets) = data
    val (input, offset) = extractCaret(inputCaret)
    val newContent = newContentInBrackets.stripPrefix("[").stripSuffix("]")

    val psiFile = createPseudoPhysicalHoconFile(getProject, input)

    inWriteCommandAction {
      val string = Iterator.iterate(psiFile.findElementAt(offset))(_.getParent)
        .collectFirst({ case hs: HString => hs }).get
      val manipulator = ElementManipulators.getManipulator(string)
      val range = manipulator.getRangeInElement(string)
      manipulator.handleContentChange(string, range, newContent)
    }

    psiFile.getText
  }
}

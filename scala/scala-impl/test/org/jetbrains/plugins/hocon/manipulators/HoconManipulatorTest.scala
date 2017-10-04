package org.jetbrains.plugins.hocon.manipulators

import com.intellij.psi.ElementManipulators
import org.jetbrains.plugins.hocon.HoconFileSetTestCase
import org.jetbrains.plugins.hocon.HoconTestUtils._
import org.jetbrains.plugins.hocon.psi.HoconPsiElement

import scala.reflect.ClassTag

/**
  * @author ghik
  */
abstract class HoconManipulatorTest[T <: HoconPsiElement : ClassTag](name: String)
  extends HoconFileSetTestCase("manipulators/" + name) {

  protected def transform(data: Seq[String]): String = {
    val Seq(inputCaret, newContentInBrackets) = data
    val (input, offset) = extractCaret(inputCaret)
    val newContent = newContentInBrackets.stripPrefix("[").stripSuffix("]")

    val psiFile = createPseudoPhysicalHoconFile(getProject, input)

    inWriteCommandAction {
      val element = Iterator.iterate(psiFile.findElementAt(offset))(_.getParent)
        .collectFirst({ case t: T => t }).get
      val manipulator = ElementManipulators.getManipulator(element)
      val range = manipulator.getRangeInElement(element)
      manipulator.handleContentChange(element, range, newContent)
    }

    psiFile.getText
  }
}

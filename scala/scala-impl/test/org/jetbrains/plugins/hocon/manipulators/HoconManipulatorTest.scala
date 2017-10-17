package org.jetbrains.plugins.hocon
package manipulators

import com.intellij.psi.ElementManipulators
import org.jetbrains.plugins.hocon.psi.HoconPsiElement

/**
  * @author ghik
  */
abstract class HoconManipulatorTest(clazz: Class[_ <: HoconPsiElement],
                                    name: String)
  extends HoconFileSetTestCase("manipulators/" + name) {

  import HoconFileSetTestCase._

  override protected def transform(data: Seq[String]): String = {
    val Seq(inputCaret, newContentInBrackets) = data
    val (input, offset) = extractCaret(inputCaret)
    val newContent = newContentInBrackets.stripPrefix("[").stripSuffix("]")

    val psiFile = createPseudoPhysicalHoconFile(input)

    inWriteCommandAction {
      val element = Iterator.iterate(psiFile.findElementAt(offset))(_.getParent)
        .find(clazz.isInstance).get

      val manipulator = ElementManipulators.getManipulator(element)
      val range = manipulator.getRangeInElement(element)
      manipulator.handleContentChange(element, range, newContent)
    }

    psiFile.getText
  }
}

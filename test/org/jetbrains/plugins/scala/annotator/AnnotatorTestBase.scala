package org.jetbrains.plugins.scala
package annotator

import com.intellij.psi.{PsiErrorElement, PsiReference}
import junit.framework.Assert._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * Pavel Fatin
 */

abstract class AnnotatorTestBase[T <: ScalaPsiElement](annotator: AnnotatorPart[T]) extends SimpleTestCase {
  final val Prefix = "object Holder { class Object; "
  final val Suffix = " }"

  protected def messages(@Language(value = "Scala", prefix = Prefix, suffix = Suffix) code: String): List[Message] = {
    val s: String = Prefix + code + Suffix
    val mock = new AnnotatorHolderMock
    val file: ScalaFile = s.parse

    assertEquals(Nil, file.depthFirst.filterByType(classOf[PsiErrorElement]).map(_.getText).toList)

    assertEquals(Nil, file.depthFirst.filterByType(classOf[PsiReference])
            .filter(_.resolve == null).map(_.getElement.getText).toList)

    file.depthFirst.filterByType(annotator.kind).foreach { it =>
      annotator.annotate(it, mock, true)
    }
    mock.annotations
  }
}
package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import junit.framework.Assert._
import com.intellij.psi.{PsiReference, PsiErrorElement}

/**
 * Pavel Fatin
 */

abstract class AnnotatorTestBase[T <: ScalaPsiElement](annotator: AnnotatorPart[T]) extends SimpleTestCase {
  protected def messages(code: String): List[Message] = {
    val s = "object Holder { class Object; " + code + " }"
    val mock = new AnnotatorHolderMock
    val file = s.parse

    assertEquals(Nil, file.depthFirst.filterByType(classOf[PsiErrorElement]).map(_.getText).toList)

    assertEquals(Nil, file.depthFirst.filterByType(classOf[PsiReference])
            .filter(_.resolve == null).map(_.getElement.getText).toList)

    file.depthFirst.filterByType(annotator.kind).foreach { it =>
      annotator.annotate(it, mock, true)
    }
    mock.annotations
  }
}
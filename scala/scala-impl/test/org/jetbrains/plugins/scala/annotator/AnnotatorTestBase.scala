package org.jetbrains.plugins.scala
package annotator

import com.intellij.psi.{PsiErrorElement, PsiReference}
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.Assert._

import scala.reflect.ClassTag

/**
 * Pavel Fatin
 */

abstract class AnnotatorTestBase[T <: ScalaPsiElement : ClassTag](annotator: AnnotatorPart[T]) extends SimpleTestCase {
  final val Prefix = "object Holder { class Object; "
  final val Suffix = " }"

  protected def messages(@Language(value = "Scala", prefix = Prefix, suffix = Suffix) code: String): Option[List[Message]] = {
    val s: String = Prefix + code + Suffix
    val file: ScalaFile = s.parse
    val mock = new AnnotatorHolderMock(file)

    val errorElements = file.depthFirst().filterByType[PsiErrorElement].map(_.getText).toList
    val notResolved = file.depthFirst().filterByType[PsiReference].filter(_.resolve == null).map(_.getElement.getText).toList
    if (shouldPass) {
      assertEquals(Nil, errorElements)
      assertEquals(Nil, notResolved)
    } else {
      if (errorElements.nonEmpty) return None
      if (notResolved.nonEmpty) return None
    }

    file.depthFirst().filterByType[T].foreach { it =>
      annotator.annotate(it, mock, typeAware = true)
    }
    Some(mock.annotations)
  }
}
package org.jetbrains.plugins.scala
package annotator

import com.intellij.psi.{PsiErrorElement, PsiReference}
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.annotator.element.ElementAnnotator
import org.jetbrains.plugins.scala.base.{SharedTestProjectToken, SimpleTestCase}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.applicability.ApplicabilityTestBase
import org.junit.Assert._

/**
 * Pavel Fatin
 */

// TODO
// In many tests, we test a particular part of annotation separately.
// On the one hand, this is a good thing, as we can test the logic in isolation.
// On the other hand, this couples a test with a very particular implementation, which makes the implementation less flexible.
// Also, the SCL-15138 (Only highlight initial, not derivative errors) meta issue requires to test the interplay between different parts of the annotator implementation.
// It's probably better to use otherwise valid code in the test, so that we can rely on the general "annotate" functionality (but we, obviously still may write specialized tests).
abstract class AnnotatorTestBase[T <: ScalaPsiElement : reflect.ClassTag] extends SimpleTestCase {

  final val Prefix = "object Holder { class Object; "
  final val Suffix = " }"

  protected def messages(@Language(value = "Scala", prefix = Prefix, suffix = Suffix) code: String): Option[List[Message]] = {
    val s: String = Prefix + code + Suffix
    val file: ScalaFile = s.parse
    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    val errorElements = file.depthFirst().filterByType[PsiErrorElement].map(_.getText).toList
    val notResolved = file.depthFirst().filterByType[PsiReference].filter(_.resolve == null).map(_.getElement.getText).toList
    if (shouldPass) {
      assertEquals(Nil, errorElements)
      assertEquals(Nil, notResolved)
    } else {
      if (errorElements.nonEmpty) return None
      if (notResolved.nonEmpty) return None
    }

    file.elements.filterByType[T].foreach(annotate(_))

    Some(mock.annotations)
  }

  protected def annotate(element: T)
                        (implicit holder: ScalaAnnotationHolder): Unit =
    ElementAnnotator.annotate(element)

  override protected def sharedProjectToken: SharedTestProjectToken =
    SharedTestProjectToken(classOf[AnnotatorTestBase[_]])
}
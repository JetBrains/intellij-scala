package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.{PsiErrorElement, PsiReference}
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.junit.Assert._

import scala.reflect.ClassTag

/**
 * Pavel Fatin
 */

// TODO
// In many tests, we test a particular part of annotation separately.
// On the one hand, this is a good thing, as we can test the logic in isolation.
// On the other hand, this couples a test with a very particular implementation, which makes the implementation less flexible.
// Also, the SCL-15138 (Only highlight initial, not derivative errors) meta issue requires to test the interplay between different parts of the annotator implementation.
// It's probably better to use otherwise valid code in the test, so that we can rely on the general "annotate" functionality (but we, obviously still may write specialized tests).
abstract class AnnotatorTestBase[T <: ScalaPsiElement : ClassTag](annotator: (T, AnnotationHolder) => Unit =
                                                                  (e: T, holder: AnnotationHolder) => e.annotate(holder, typeAware = true)) extends SimpleTestCase {

  final val Prefix = "object Holder { class Object; "
  final val Suffix = " }"

  def this(part: AnnotatorPart[T]) = this((e, holder) => part.annotate(e, holder, typeAware = true))

  protected def messages(@Language(value = "Scala", prefix = Prefix, suffix = Suffix) code: String): Option[List[Message]] = {
    val s: String = Prefix + code + Suffix
    val file: ScalaFile = s.parse
    val mock = new AnnotatorHolderMock(file)

    val errorElements = file.depthFirst().instancesOf[PsiErrorElement].map(_.getText).toList
    val notResolved = file.depthFirst().instancesOf[PsiReference].filter(_.resolve == null).map(_.getElement.getText).toList
    if (shouldPass) {
      assertEquals(Nil, errorElements)
      assertEquals(Nil, notResolved)
    } else {
      if (errorElements.nonEmpty) return None
      if (notResolved.nonEmpty) return None
    }

    file.elements.instancesOf[T].foreach(annotator(_, mock))

    Some(mock.annotations)
  }
}
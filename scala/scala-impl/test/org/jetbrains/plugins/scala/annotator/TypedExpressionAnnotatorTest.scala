package org.jetbrains.plugins.scala
package annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.annotator.element.ScTypedExpressionAnnotator
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScTypedExpression
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class TypedExpressionAnnotatorTest extends SimpleTestCase {
  private final val Header = "class A; class B; object A extends A; object B extends B\n"

  def testFine(): Unit = {
    assertMatches(messages("A: A")) {
      case Nil =>
    }
  }

  def testTypeMismatch(): Unit = {
    assertMatches(messages("B: A")) {
      case Error("A", UpcastingError()) :: Nil =>
    }
  }

  def testTypeMismatchMessage(): Unit = {
    assertMatches(messages("B: A")) {
      case Error(_, "Cannot upcast B.type to A") :: Nil =>
    }
  }

  //todo: requires Function1 trait in scope
  /*def testImplicitConversion {
    assertMatches(messages("implicit def toA(b: B) = A; B: A")) {
      case Nil =>
    }
  }*/


  def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val file = (Header + code).parse
    val expression = file.depthFirst().findByType[ScTypedExpression].get

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)
    ScTypedExpressionAnnotator.annotate(expression, typeAware = true)
    mock.annotations
  }

  private val UpcastingError = ContainsPattern("Cannot upcast")
}

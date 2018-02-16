package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.annotator.template.NotFoundImplicitParameters

/**
  * Nikolay.Tropin
  * 16-Feb-18
  */
class ImplicitParametersAnnotatorTest extends AnnotatorTestBase(NotFoundImplicitParameters) {
  private def notFound(types: String*) = NotFoundImplicitParameters.message(types)

  def testCorrectImplicits(): Unit = assertNothing(messages(
    """def implicitly[T](implicit e: T) = e
      |implicit val implicitInt = 42
      |val v1: Int = implicitly
      |val v2 = implicitly[Int]""".stripMargin
  ))

  def testUnresolvedImplicits(): Unit = assertMatches(messages("def implicitly[T](implicit e: T) = e; implicit val implicitInt = implicitly[Int]")) {
    case Error("implicitly[Int]", m) :: Nil if m == notFound("Int") =>
  }

  def testPair(): Unit = assertMatches(messages("def foo(implicit i: Int, d: Double) = (i, d); foo")) {
    case Error("foo", m) :: Nil if m == notFound("Int", "Double") =>
  }
}

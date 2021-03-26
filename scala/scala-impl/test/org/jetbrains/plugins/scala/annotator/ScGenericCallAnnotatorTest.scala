package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.annotator.element.ScReferenceAnnotator
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference


class ScGenericCallAnnotatorTest extends SimpleTestCase {
  def testTooFewTypeParameter(): Unit = {
    assertMessages(messages("test[Int]"))(
      Error("t]", "Unspecified type parameters: Y")
    )
    assertMessages(messages("test[]"))(
      Error("[]", "Unspecified type parameters: X, Y")
    )
  }

  def testTooManyTypeParameter(): Unit = {
    assertMessages(messages("test[Int, Int, Int]"))(
      Error(", I", "Too many type arguments for test[X, Y]")
    )
    assertMessages(messages("test[Int, Int, Boolean, Int]"))(
      Error(", B", "Too many type arguments for test[X, Y]")
    )

    assertMessages(messages("def nop = (); nop[Int]"))(
      Error("[Int]", "nop does not take type arguments")
    )
  }

  def testBoundViolation(): Unit = {
    assertMessages(messages("test2[A, A]"))(
      Error("A", "Type A does not conform to upper bound B of type parameter X"),
    )

    assertMessages(messages("test2[C, C]"))(
      Error("C", "Type C does not conform to lower bound B of type parameter Y"),
    )

    assertMessages(messages("test2[A, C]"))(
      Error("A", "Type A does not conform to upper bound B of type parameter X"),
      Error("C", "Type C does not conform to lower bound B of type parameter Y"),
    )
  }

  def testHigherKindedTypes(): Unit = {
    assertMessages(messages("class Test[X, Y]; testHk[Test]"))()

    assertMessages(messages("testHk[A]"))(
      Error("A", "Expected type constructor CC[X >: B <: B, _]")
    )

    assertMessages(messages("testHk[HkArg]"))(
      Error("HkArg", "Type constructor HkArg does not conform to CC[X >: B <: B, _]")
    )
  }

  def testTypeConstructorParameter(): Unit = {
    assertNothing(messages(
      """
        |def test[F[_]]: Unit = ()
        |
        |def context[G[_]]: Unit = test[G]
        |
        |""".stripMargin
    ))
  }

  def testInferredUpperbound(): Unit = {
    assertNothing(messages(
      """
        |trait A
        |trait B extends A
        |
        |def test[Up, X <: Up]: Unit = ()
        |
        |test[A, B]
        |
        |""".stripMargin
    ))
  }

  def testSelfInUpperBound(): Unit = {
    assertNothing(messages(
      """
        |trait Base[A]
        |class Impl extends Base[Impl]
        |
        |def test[X <: Base[X]]: Unit = ()
        |
        |
        |test[Impl]
        |
        |""".stripMargin
    ))
  }

  def testOuterTypeParameter(): Unit = {
    assertMessages(messages(
      """
        |class Context[C <: Ctx, Ctx] {
        |
        |  def test[X >: Ctx <: Ctx]: Unit = ()
        |
        |  test[C]
        |}
        |""".stripMargin
    ))(
      Error("C", "Type C does not conform to lower bound Ctx of type parameter X")
    )
  }

  def testHkBound(): Unit = {

    assertNothing(messages(
      """
        |trait M[F]
        |def test[X[A] <: M[X]]: Unit = ()
        |
        |def context[Y[_] <: M[Y]]: Unit = test[Y]
        |""".stripMargin
    ))
  }

  def testTypeBoundContainingTypeParamInTypeConstructor(): Unit = {
    assertNothing(messages(
      """
        |trait Trait[X]
        |def test[A[X <: Trait[X]]]: Unit = ()
        |
        |def context[B[Y <: Trait[Y]]]: Unit = test[B]
        |""".stripMargin
    ))
  }

  def testAnyAsTypeConstructor(): Unit = {
    assertNothing(messages(
      """
        |def hk[TC[_]]: Unit = ()
        |
        |hk[Any]
        |""".stripMargin
    ))
  }

  def testNothingAsTypeConstructor(): Unit = {
    assertNothing(messages(
      """
        |def hk[TC[_]]: Unit = ()
        |
        |hk[Nothing]
        |""".stripMargin
    ))
  }

  def testWildcard(): Unit = {
    assertNothing(messages(
      """
        |trait Growable
        |def lazyCombiner[Buff <: Growable]: Unit = ()
        |
        |object Test {
        |  lazyCombiner[_]
        |}
        |
        |""".stripMargin
    ))
  }

  def testUnresolved(): Unit = {
    assertMessages(messages(
      """
        |trait A
        |trait B extends A
        |
        |def test[X <: A]: Unit = ()
        |test[hahaha]
        |
        |""".stripMargin
    ))(
      Error("hahaha", "Cannot resolve symbol hahaha")
    )
  }

  def testUpperBoundIsTypeConstructorSetToAny(): Unit = {
    assertNothing(messages(
      """
        |def test[X <: Upper[X], Upper[_]]: Unit = ()
        |
        |def context[T] = test[T, Any]
        |""".stripMargin
    ))
  }

  def testLowerBoundIsTypeConstructorSetToNothing(): Unit = {
    assertNothing(messages(
      """
        |trait A
        |def test[X >: Lower[X], Lower[_]] = ()
        |
        |def context[T >: A]: Unit = test[T, Nothing]
        |""".stripMargin
    ))
  }

  def testParameterizedProjectionType(): Unit = {
    assertNothing(messages(
      """
        |trait ParIterableLike[+TT] {
        |  def test[UU >: TT]: Unit
        |}
        |
        |trait ParSeqLike[+T] extends ParIterableLike[T] {
        |  def context[U >: T]: Unit = {
        |    test[U]
        |  }
        |}
        |""".stripMargin
    ))
  }

  def messages(code: String): List[Message] = {
    val header =
      """
        |trait A
        |trait B extends A
        |trait C extends B
        |
        |def test[X, Y]: Unit = ()
        |def test2[X <: B, Y >: B]: Unit = ()
        |def testHk[CC[X >: B <: B, _]]: Unit = ()
        |
        |class HkArg[X >: A, Y <: C]
        |
        |""".stripMargin

    val file: ScalaFile = (header + code).parse

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    file.depthFirst().filterByType[ScReference].foreach { pte =>
      ScReferenceAnnotator.annotate(pte, typeAware = true)
    }

    mock.annotations
  }
}
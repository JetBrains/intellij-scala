package org.jetbrains.plugins.scala.annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.annotator.element.ScTypeBoundsOwnerAnnotator
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam


class ScTypeBoundsOwnerAnnotatorTest extends AnnotatorSimpleTestCase {
  import Message._

  def testNoErrors(): Unit = assertNothing(
    messages(
      """
        |trait Foo[A]
        |def foo[T : Foo](t: T): T = t
        |""".stripMargin)
  )

  def testMissingTypeArguments(): Unit = {
    assertMessages(
      messages(
        """
          |trait Foo[A, B]
          |def foo[T : Foo](): Unit = ???
          |""".stripMargin
      )
    )(Error("Foo", "Unspecified type parameters: B"))

    assertMessages(
      messages(
        """
          |trait Bar[A, B, C, D]
          |def bar[T : Bar](): Unit = ???
          |""".stripMargin
      )
    )(Error("Bar", "Unspecified type parameters: B, C, D"))
  }

  def testExtraTypeArguments(): Unit = assertMessages(
    messages(
      """
        |trait Foo
        |def foo[A: Foo]: Unit = ???
        |""".stripMargin
    )
  )(Error("Foo", "Foo does not take type arguments"))

  def testKindMismatch(): Unit = {
    assertNothing(
      messages(
        """
          |trait Foo[F[_]]
          |def foo[A[_]: Foo]: Unit = ???
          |""".stripMargin
      )
    )

    assertMessages(
      messages(
        """
          |trait Foo[F[_]]
          |def foo[A: Foo]: Unit = ???
          |""".stripMargin
      )
    )(Error("Foo", "Expected type constructor F[_]"))

    assertMessages(
      messages(
        """
          |trait Foo[F[_, _]]
          |def foo[A[_]: Foo]: Unit = ???
          |""".stripMargin
      )
    )(Error("Foo", "Type constructor A does not conform to F[_, _]"))

    assertMessages(
      messages(
        """
          |trait Foo[F[_]]
          |def foo[A[_, _]: Foo]: Unit = ???
          |""".stripMargin
      )
    )(Error("Foo", "Type constructor A does not conform to F[_] "))
  }

  def testParamDoesNotConform(): Unit = {
    assertMessages(
      messages(
        """
          |trait Bar
          |trait Foo[A <: Bar]
          |def foo[A : Foo]: Unit = ???
          |""".stripMargin)
    )(Error("Foo", "Type A does not conform to upper bound Bar of type parameter A"))

    assertNothing(
      messages(
        """
          |trait Foo[A <: String]
          |def foo[A <: String : Foo]: Unit = ???
          |""".stripMargin
      )
    )

    assertMessages(
      messages(
        """
          |trait Bar
          |trait Foo[A >: Bar]
          |def foo[A : Foo]: Unit = ???
          |""".stripMargin)
    )(Error("Foo", "Type A does not conform to lower bound Bar of type parameter A"))

    assertNothing(
      messages(
        """
          |trait Bar
          |trait Baz extends Bar
          |trait Foo[A >: Baz]
          |def foo[A >: Bar : Foo]: Unit = ???
          |""".stripMargin)
    )
  }

  def testSCL23575(): Unit = assertNothing(
    messages(
      """
        |sealed trait MyTrait[V <: Ordered[V]] {
        |  def minor(v: V): String
        |}
        |
        |object MyExample {
        |  def foo[V <: Ordered[V] : MyTrait](version: V): String = ???
        |}
        |""".stripMargin
    )
  )

  def testSCL22501(): Unit = {
    assertNothing(
      messages(
        """
          |trait MyIterable[+A]
          |
          |trait Aggregate1[F[_] <: MyIterable[_]]
          |trait Aggregate2[F[A] <: MyIterable[A]]
          |
          |//noinspection NotImplementedCode
          |object Example {
          |  //without type alias
          |  val aOk1: Aggregate1[MyIterable] = ???
          |  val aOk2: Aggregate2[MyIterable] = ???
          |
          |  type AggregateValue[T] <: MyIterable[T]
          |
          |  //with type alias
          |  val aBad1: Aggregate1[AggregateValue] = ???
          |  val aBad2: Aggregate2[AggregateValue] = ???
          |}
          |""".stripMargin
      )
    )

    assertNothing(
      messages(
        """
          |trait Key[T]
          |trait Keyed[K[T] <: Key[T]]
          |trait HasKey {
          |  type K[T] <: Key[T]
          |  def keyed: Keyed[K]
          |}
          |""".stripMargin
      )
    )
  }

  def messages(@Language("Scala")code: String): List[Message] = {
    val file =
      s"""
         |object Test {
         |  $code
         |}
         |""".stripMargin.parse


    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    file
      .depthFirst()
      .filterByType[ScTypeParam]
      .foreach(ScTypeBoundsOwnerAnnotator.annotate(_, typeAware = true))

    mock.annotations
  }
}

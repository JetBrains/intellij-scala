package org.jetbrains.plugins.scala.annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.annotator.element.ScBoundsOwnerAnnotator
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam

class ScBoundsOwnerAnnotatorTest extends AnnotatorSimpleTestCase {
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
      .foreach(ScBoundsOwnerAnnotator.annotate(_, typeAware = true))

    mock.annotations
  }
}

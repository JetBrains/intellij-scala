package org.jetbrains.plugins.scala.annotator

import com.intellij.psi.PsiFileFactory
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.Scala3Language
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScDerivesClause

class ScDerivesClauseAnnotatorTest extends AnnotatorSimpleTestCase {
  import Message._

  def testImplicitMissing(): Unit =
    assertMessages(
      messages(
        """
          |trait Foo[A]
          |object Foo { def derived[A](implicit ev: Foo[Int]): Foo[A] = ??? }
          |
          |case class A() derives Foo
          |""".stripMargin
      )
    )(
      Error("Foo", "No implicit arguments of type: Foo[Int]"),
      Error("Foo", "Expression of type Foo[Nothing] doesn't conform to expected type Foo[A]")
    )

  def testTypesDoNotMatch(): Unit =
    assertMessages(
      messages(
        """
          |trait Foo[A]
          |object Foo { def derived: Foo[Int] = ??? }
          |
          |case class A() derives Foo
          |""".stripMargin
      )
    )(Error("Foo", "Expression of type Foo[Int] doesn't conform to expected type Foo[A]"))

  def testNonClassType(): Unit =
    assertMessages(
      messages(
        """
          |trait Foo[A] {
          |  case class B() derives A
          |}
          |""".stripMargin
      )
    )(Error("A", "Scala class/trait expected"))

  def testOverlapingKindsDoNotMatch(): Unit =
    assertMessages(
      messages(
        """
          |trait Foo[F[_, _]]
          |object Foo { def derived[F[_, _]]: Foo[F] = ??? }
          |
          |case class A[A, B[_]]() derives Foo
          |""".stripMargin
      )
    )(Error("Foo", "A cannot be unified with the type argument of Foo"))

  def testNoTypeParameters(): Unit =
    assertMessages(
      messages(
        """
          |trait Foo
          |object Foo { def derived: Foo = ??? }
          |
          |case class A() derives Foo
          |""".stripMargin
      )
    )(Error("Foo", "Foo cannot be derived, it has no type parameters"))

  def testTooManyTypeParameters(): Unit =
    assertMessages(
      messages(
        """
          |trait Foo[A, B, C]
          |object Foo { def derived[A, B, C]: Foo[A, B, C] = ??? }
          |
          |case class A() derives Foo
          |""".stripMargin
      )
    )(Error("Foo", "A cannot be unified with the type argument of Foo"))

  def testNoCompanion(): Unit =
    assertMessages(
      messages(
        """
          |trait Foo[A]
          |
          |case class A() derives Foo
          |""".stripMargin
      )
    )(Error("Foo", "Foo cannot be derived, it has no companion object"))

  def testNoDerivedMember(): Unit =
    assertMessages(
      messages(
        """
          |trait Foo[A]
          |object Foo
          |
          |case class A() derives Foo
          |""".stripMargin
      )
    )(Error("Foo", "Value derived is not a member of object Foo"))

  def messages(@Language("Scala")code: String): List[Message] = {
    val annotator = new ScalaAnnotator()

    val file =
      PsiFileFactory
        .getInstance(fixture.getProject)
        .createFileFromText("foo.scala", Scala3Language.INSTANCE, code)
        .asInstanceOf[ScalaFile]

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    file
      .depthFirst()
      .filterByType[ScDerivesClause]
      .foreach(annotator.annotate(_))

    mock.annotations
  }
}

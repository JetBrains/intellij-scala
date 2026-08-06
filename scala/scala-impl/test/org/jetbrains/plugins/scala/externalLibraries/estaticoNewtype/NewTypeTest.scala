package org.jetbrains.plugins.scala.externalLibraries.estaticoNewtype

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.macros.SynteticInjectorsTestUtils._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}

class NewTypeTest extends ScalaLightCodeInsightFixtureTestCase {

  override def additionalLibraries: Seq[LibraryLoader] = Seq(IvyManagedLoader("io.estatico" %% "newtype" % "0.4.3"))

  private def getSourceElement(text: String): ScObject = {
    val normalized = text.withNormalizedSeparator.trim
    val caretPos = normalized.indexOf("<caret>")
    val file = configureFromFileText(normalized.replace("<caret>", ""))
    val cls = PsiTreeUtil.findElementOfClassAtOffset(file, caretPos, classOf[ScTypeDefinition], false)
    cls.fakeCompanionModule.getOrElse(
      throw new IllegalArgumentException(s"Companion object not found for class at caret in $text.")
    )
  }

  def testSimpleType(): Unit = {
    val text =
      s"""
         |import _root_.io.estatico.newtype.macros.newtype
         |
         |package object types {
         |  @newtype <caret>case class WidgetId(toInt: Int)
         |}
       """.stripMargin

    val widgetIdObject = getSourceElement(text)

    val syntheticStructure =
      `object`("WidgetId")(
        `def`("deriving", "[TC] TC[Int] => TC[types.WidgetId]"),
        `implicit`("unsafeWrap", "_root_.io.estatico.newtype.Coercible[Int, types.WidgetId]"),
        `implicit`("unsafeUnwrap", "_root_.io.estatico.newtype.Coercible[types.WidgetId, Int]"),
        `implicit`("unsafeWrapM", "[M] Coercible[M[Int], M[types.WidgetId]]"),
        `implicit`("unsafeUnwrapM", "[M] Coercible[M[types.WidgetId], M[Int]]"),
        `implicit`("cannotWrapArrayAmbiguous1", "_root_.io.estatico.newtype.Coercible[_root_.scala.Array[Int], _root_.scala.Array[types.WidgetId]]"),
        `implicit`("cannotWrapArrayAmbiguous2", "_root_.io.estatico.newtype.Coercible[_root_.scala.Array[Int], _root_.scala.Array[types.WidgetId]]"),
        `implicit`("cannotUnwrapArrayAmbiguous1", "_root_.io.estatico.newtype.Coercible[_root_.scala.Array[types.WidgetId], _root_.scala.Array[Int]]"),
        `implicit`("cannotUnwrapArrayAmbiguous2", "_root_.io.estatico.newtype.Coercible[_root_.scala.Array[types.WidgetId], _root_.scala.Array[Int]]")
      )

    widgetIdObject mustBeLike syntheticStructure
  }

  def testHKT1(): Unit = {
    val text =
      s"""
         |import _root_.io.estatico.newtype.macros.newtype
         |
         |package object types {
         |  @newtype <caret>case class Maybe[A](toOption: Option[A])
         |}
       """.stripMargin

    val maybeObject = getSourceElement(text)

    val syntheticStructure =
      `object`("Maybe")(
        `def`("deriving", "[TC, A] TC[Option[A]] => TC[types.Maybe[A]]"),
        `def`("derivingK", "[TC] TC[Option] => TC[types.Maybe]"),
        `implicit`("unsafeWrap", "[A] Coercible[Option[A], types.Maybe[A]]"),
        `implicit`("unsafeUnwrap", "[A] Coercible[types.Maybe[A], Option[A]]"),
        `implicit`("unsafeWrapM", "[M, A] Coercible[M[Option[A]], M[types.Maybe[A]]]"),
        `implicit`("unsafeUnwrapM", "[M, A] Coercible[M[types.Maybe[A]], M[Option[A]]]"),
        `implicit`("unsafeWrapK", "[T] Coercible[T[Option], T[types.Maybe]]"),
        `implicit`("unsafeUnwrapK", "[T] Coercible[T[types.Maybe], T[Option]]"),
        `implicit`("cannotWrapArrayAmbiguous1", "[A] Coercible[Array[Option[A]], Array[types.Maybe[A]]]"),
        `implicit`("cannotWrapArrayAmbiguous2", "[A] Coercible[Array[Option[A]], Array[types.Maybe[A]]]"),
        `implicit`("cannotUnwrapArrayAmbiguous1", "[A] Coercible[Array[types.Maybe[A]], Array[Option[A]]]"),
        `implicit`("cannotUnwrapArrayAmbiguous2", "[A] Coercible[Array[types.Maybe[A]], Array[Option[A]]]")
      )

    maybeObject mustBeLike syntheticStructure
  }

  def testHKT2(): Unit = {
    val text =
      s"""
         |import _root_.io.estatico.newtype.macros.newtype
         |
         |package object types {
         |  @newtype <caret>case class Branch[A, B](toEither: Either[A, B])
         |}
       """.stripMargin

    val branchObject = getSourceElement(text)

    val syntheticStructure =
      `object`("Branch")(
        `def`("deriving", "[TC, A, B] TC[Either[A, B]] => TC[types.Branch[A, B]]"),
        `def`("derivingK", "[TC] TC[Either] => TC[types.Branch]"),
        `implicit`("unsafeWrap", "[A, B] Coercible[Either[A, B], types.Branch[A, B]]"),
        `implicit`("unsafeUnwrap", "[A, B] Coercible[types.Branch[A, B], Either[A, B]]"),
        `implicit`("unsafeWrapM", "[M, A, B] Coercible[M[Either[A, B]], M[types.Branch[A, B]]]"),
        `implicit`("unsafeUnwrapM", "[M, A, B] Coercible[M[types.Branch[A, B]], M[Either[A, B]]]"),
        `implicit`("unsafeWrapK", "[T] Coercible[T[Either], T[types.Branch]]"),
        `implicit`("unsafeUnwrapK", "[T] Coercible[T[types.Branch], T[Either]]"),
        `implicit`("cannotWrapArrayAmbiguous1", "[A, B] Coercible[Array[Either[A, B]], Array[types.Branch[A, B]]]"),
        `implicit`("cannotWrapArrayAmbiguous2", "[A, B] Coercible[Array[Either[A, B]], Array[types.Branch[A, B]]]"),
        `implicit`("cannotUnwrapArrayAmbiguous1", "[A, B] Coercible[Array[types.Branch[A, B]], Array[Either[A, B]]]"),
        `implicit`("cannotUnwrapArrayAmbiguous2", "[A, B] Coercible[Array[types.Branch[A, B]], Array[Either[A, B]]]")
      )

    branchObject mustBeLike syntheticStructure
  }

  def testHKTWithHKTParam(): Unit = {
    val text =
      s"""
         |import _root_.io.estatico.newtype.macros.newtype
         |
         |package object types {
         |  trait Functor[F[_]]
         |  @newtype <caret>case class HKTWrapper[F[_]](fun: Functor[F])
         |}
       """.stripMargin

    val branchObject = getSourceElement(text)

    val syntheticStructure =
      `object`("HKTWrapper")(
        `def`("deriving", "[TC, F] TC[types.Functor[F]] => TC[types.HKTWrapper[F]]"),
        `def`("derivingK", "[TC] TC[types.Functor] => TC[types.HKTWrapper]"),
        `implicit`("unsafeWrap", "[F] Coercible[types.Functor[F], types.HKTWrapper[F]]"),
        `implicit`("unsafeUnwrap", "[F] Coercible[types.HKTWrapper[F], types.Functor[F]]"),
        `implicit`("unsafeWrapM", "[M, F] Coercible[M[types.Functor[F]], M[types.HKTWrapper[F]]]"),
        `implicit`("unsafeUnwrapM", "[M, F] Coercible[M[types.HKTWrapper[F]], M[types.Functor[F]]]"),
        `implicit`("unsafeWrapK", "[T] Coercible[T[types.Functor], T[types.HKTWrapper]]"),
        `implicit`("unsafeUnwrapK", "[T] Coercible[T[types.HKTWrapper], T[types.Functor]]"),
        `implicit`("cannotWrapArrayAmbiguous1", "[F] Coercible[Array[types.Functor[F]], Array[types.HKTWrapper[F]]]"),
        `implicit`("cannotWrapArrayAmbiguous2", "[F] Coercible[Array[types.Functor[F]], Array[types.HKTWrapper[F]]]"),
        `implicit`("cannotUnwrapArrayAmbiguous1", "[F] Coercible[Array[types.HKTWrapper[F]], Array[types.Functor[F]]]"),
        `implicit`("cannotUnwrapArrayAmbiguous2", "[F] Coercible[Array[types.HKTWrapper[F]], Array[types.Functor[F]]]")
      )

    branchObject mustBeLike syntheticStructure
  }

  def testHKTWithHKTParamAndOthers(): Unit = {
    val text =
      s"""
         |import _root_.io.estatico.newtype.macros.newtype
         |
         |package object types {
         |  trait ConstK[F[_], A]
         |  @newtype <caret>case class HKTPlusWrapper[F[_], A](fun: ConstK[F, A])
         |}
       """.stripMargin

    val branchObject = getSourceElement(text)

    val syntheticStructure =
      `object`("HKTPlusWrapper")(
        `def`("deriving", "[TC, F, A] TC[types.ConstK[F, A]] => TC[types.HKTPlusWrapper[F, A]]"),
        `def`("derivingK", "[TC] TC[types.ConstK] => TC[types.HKTPlusWrapper]"),
        `implicit`("unsafeWrap", "[F, A] Coercible[types.ConstK[F, A], types.HKTPlusWrapper[F, A]]"),
        `implicit`("unsafeUnwrap", "[F, A] Coercible[types.HKTPlusWrapper[F, A], types.ConstK[F, A]]"),
        `implicit`("unsafeWrapM", "[M, F, A] Coercible[M[types.ConstK[F, A]], M[types.HKTPlusWrapper[F, A]]]"),
        `implicit`("unsafeUnwrapM", "[M, F, A] Coercible[M[types.HKTPlusWrapper[F, A]], M[types.ConstK[F, A]]]"),
        `implicit`("unsafeWrapK", "[T] Coercible[T[types.ConstK], T[types.HKTPlusWrapper]]"),
        `implicit`("unsafeUnwrapK", "[T] Coercible[T[types.HKTPlusWrapper], T[types.ConstK]]"),
        `implicit`("cannotWrapArrayAmbiguous1", "[F, A] Coercible[Array[types.ConstK[F, A]], Array[types.HKTPlusWrapper[F, A]]]"),
        `implicit`("cannotWrapArrayAmbiguous2", "[F, A] Coercible[Array[types.ConstK[F, A]], Array[types.HKTPlusWrapper[F, A]]]"),
        `implicit`("cannotUnwrapArrayAmbiguous1", "[F, A] Coercible[Array[types.HKTPlusWrapper[F, A]], Array[types.ConstK[F, A]]]"),
        `implicit`("cannotUnwrapArrayAmbiguous2", "[F, A] Coercible[Array[types.HKTPlusWrapper[F, A]], Array[types.ConstK[F, A]]]")
      )

    branchObject mustBeLike syntheticStructure
  }

  def testDifferentShapedHKT(): Unit = {
    val text =
      s"""
         |import _root_.io.estatico.newtype.macros.newtype
         |
         |package object types {
         |  @newtype <caret>case class EitherT[F[_], L, R](x: F[Either[L, R]])
         |}
       """.stripMargin

    val eitherTObject = getSourceElement(text)

    val syntheticStructure =
      `object`("EitherT")(
        `def`("deriving", "[TC, F, L, R] TC[F[Either[L, R]]] => TC[types.EitherT[F, L, R]]"),
        // TODO: Add derivingK, unsafeWrapK and unsafeUnwrapK when it is possible to specify their types properly
        `implicit`("unsafeWrap", "[F, L, R] Coercible[F[Either[L, R]], types.EitherT[F, L, R]]"),
        `implicit`("unsafeUnwrap", "[F, L, R] Coercible[types.EitherT[F, L, R], F[Either[L, R]]]"),
        `implicit`("unsafeWrapM", "[M, F, L, R] Coercible[M[F[Either[L, R]]], M[types.EitherT[F, L, R]]]"),
        `implicit`("unsafeUnwrapM", "[M, F, L, R] Coercible[M[types.EitherT[F, L, R]], M[F[Either[L, R]]]]"),
        `implicit`("cannotWrapArrayAmbiguous1", "[F, L, R] Coercible[Array[F[Either[L, R]]], Array[types.EitherT[F, L, R]]]"),
        `implicit`("cannotWrapArrayAmbiguous2", "[F, L, R] Coercible[Array[F[Either[L, R]]], Array[types.EitherT[F, L, R]]]"),
        `implicit`("cannotUnwrapArrayAmbiguous1", "[F, L, R] Coercible[Array[types.EitherT[F, L, R]], Array[F[Either[L, R]]]]"),
        `implicit`("cannotUnwrapArrayAmbiguous2", "[F, L, R] Coercible[Array[types.EitherT[F, L, R]], Array[F[Either[L, R]]]]")
      )

    eitherTObject mustBeLike syntheticStructure
  }
}

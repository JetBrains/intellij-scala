package org.jetbrains.plugins.scala
package lang
package macros

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter._
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.lang.macros.SynteticInjectorsTestUtils._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}

class SimulacrumTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  private val caret = "<caret>"

  override implicit val version: ScalaVersion = Scala_2_12

  override protected def additionalLibraries(): Seq[LibraryLoader] =
    Seq(IvyManagedLoader("com.github.mpilquist" %% "simulacrum" % "0.14.0"))

  private def getSourceElement(text: String): ScObject = {
    val normalized = normalize(text)
    val caretPos = normalized.indexOf("<caret>")
    configureFromFileTextAdapter("dummy.scala", normalized.replace("<caret>", ""))
    val cls = PsiTreeUtil.findElementOfClassAtOffset(getFileAdapter, caretPos, classOf[ScTypeDefinition], false)
    cls.fakeCompanionModule.getOrElse(
      throw new IllegalArgumentException(s"Companion object not found for class at caret in $text.")
    )
  }

  private def applyMethod(className: String, typeParam: String): SyntheticMethod =
    `def`("apply", s"[$typeParam] $className[$typeParam] => $className[$typeParam]")

  def testProperType(): Unit = {
    val text =
      s"""
         |import simulacrum._
         |
         |@typeclass trait Se${caret}migroup[A] {
         |  @op("|+|") def append(x: A, y: A): A
         |}
       """.stripMargin

    val semigroupObject = getSourceElement(text)

    val syntheticStructure =
      `object`("Semigroup")(
        applyMethod("Semigroup", "A"),
        `def`("apply", "[A] Semigroup[A] => Semigroup[A]"),
        `trait`("Ops[A]")(
          `def`("self", "A"),
          `def`("|+|", "A => A")
        ),
        `trait`("ToSemigroupOps") `with` `implicit`("toSemigroupOps", "[A] A => Semigroup[A] => Semigroup.Ops[A]"),
        `trait`("AllOps[A] extends Semigroup.Ops[A]"),
        `object`("nonInheritedOps extends Semigroup.ToSemigroupOps"),
        `object`("ops") `with` `implicit`("toAllSemigroupOps", "[A] A => Semigroup[A] => Semigroup.AllOps[A]")
      )

    semigroupObject mustBeExactly syntheticStructure
  }

  def testInheritance(): Unit = {
    val text =
      s"""
         |import simulacrum._
         |
         |@typeclass trait Semigroup[A] {
         |  @op("|+|") def append(x: A, y: A): A
         |}
         |
         |@typeclass trait Mo${caret}noid[B] extends Semigroup[B] {
         |  def id: A
         |  @op(name = "<>", alias = true)
         |  def mappend(x: B, y: B): B
         |}
       """.stripMargin

    val monoidObject = getSourceElement(text)

    val syntheticStructure =
      `object`("Monoid")(
        applyMethod("Monoid", "B"),
        `trait`("Ops[B]")(
          `def`("self", "B"),
          `def`("<>", "B => B"),
          `def`("mappend", "B => B")
        ),
        `trait`("ToMonoidOps") `with` `implicit`("toMonoidOps", "[B] B => Monoid[B] => Monoid.Ops[B]"),
        `trait`("AllOps[B] extends Monoid.Ops[B] with Semigroup.AllOps[B]"),
        `object`("nonInheritedOps extends Monoid.ToMonoidOps"),
        `object`("ops") `with` `implicit`("toAllMonoidOps", "[B] B => Monoid[B] => Monoid.AllOps[B]"),
      )

    monoidObject mustBeExactly syntheticStructure
  }

  def testInheritanceComplex(): Unit = {
    val text =
      s"""
         |import simulacrum._
         |
         |@typeclass trait Functor[F[_]] {
         |  def map[A, B](fa: F[A])(f: A => B): F[B]
         |}
         |
         |@typeclass trait Applicative[F[_]] extends Functor[F] {
         |  def pure[A](a: => A): F[A]
         |  def ap[A, B](fa: F[A])(f: F[A => B]): F[B]
         |  override def map[A, B](fa: F[A])(f: A => B): F[B] =
         |    ap(fa)(pure(f))
         |}
         |
         |@typeclass trait Monad[F[_]] extends Applicative[F] {
         |  @op(">>=", alias = true) def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
         |  override def ap[A, B](fa: F[A])(f: F[A => B]): F[B] =
         |    flatMap(f)(map(fa))
         |  override def map[A, B](fa: F[A])(f: A => B): F[B] =
         |    flatMap(fa)(a => pure(f(a)))
         |}
         |
         |@typeclass trait PlusEmpty[F[_]] {
         |  def empty[A]: F[A]
         |}
         |
         |@typeclass trait MonadPl${caret}us[F[_]] extends Monad[F] with PlusEmpty[F] {
         |  self =>
         |  class WithFilter[A](fa: F[A], p: A => Boolean) {
         |    def map[B](f: A => B): F[B] = self.map(filter(fa)(p))(f)
         |    def flatMap[B](f: A => F[B]): F[B] = self.flatMap(filter(fa)(p))(f)
         |    def withFilter(q: A => Boolean): WithFilter[A] = new WithFilter[A](fa, x => p(x) && q(x))
         |  }
         |
         |  def withFilter[A](fa: F[A])(p: A => Boolean): WithFilter[A] = new WithFilter[A](fa, p)
         |  def filter[A](fa: F[A])(f: A => Boolean) =
         |    flatMap(fa)(a => if (f(a)) pure(a) else empty[A])
         |}
         |
       """.stripMargin

    val monadPlusObject = getSourceElement(text)

    val syntheticStructure =
      `object`("MonadPlus")(
        applyMethod("MonadPlus", "F"),
        `trait`("Ops[F[_], LP0]")(
          `def`("self", "F[LP0]"),
          `def`("withFilter", "(LP0 => Boolean) => MonadPlus.this.WithFilter[LP0]"),
          `def`("filter", "(LP0 => Boolean) => F[LP0]")
        ),
        `trait`("ToMonadPlusOps") `with` `implicit`("toMonadPlusOps", "[F, LP0] F[LP0] => MonadPlus[F] => MonadPlus.Ops[F, LP0]"),
        `trait`("AllOps[F[_], LP0] extends MonadPlus.Ops[F, LP0] with Monad.AllOps[F, LP0] with PlusEmpty.AllOps[F, LP0]"),
        `object`("nonInheritedOps extends MonadPlus.ToMonadPlusOps"),
        `object`("ops") `with` `implicit`("toAllMonadPlusOps", "[F, LP0] F[LP0] => MonadPlus[F] => MonadPlus.AllOps[F, LP0]"),
      )

    monadPlusObject mustBeExactly syntheticStructure
  }

  def testNestedTypeParam(): Unit = {
    val text =
      s"""
         |import simulacrum._
         |
         |trait Applicative[F[_]]
         |
         |@typeclass trait Trav${caret}erse[F[_]] {
         |  def sequence[G[_]: Applicative, A](fga: F[G[A]): G[F[A]]
         |}
       """.stripMargin

    val traverseObject = getSourceElement(text)

    val syntheticStructure =
      `object`("Traverse")(
        applyMethod("Traverse", "F"),
        `trait`("Ops[F[_], LP0]")(
          `def`("self", "F[LP0]"),
          `def`("sequence", "[G, A] (Applicative[G], LP0 <:< G[A]) => G[F[A]]")
        ),
        `trait`("ToTraverseOps") `with` `implicit`("toTraverseOps", "[F, LP0] F[LP0] => Traverse[F] => Traverse.Ops[F, LP0]"),
        `trait`("AllOps[F[_], LP0] extends Traverse.Ops[F, LP0]"),
        `object`("nonInheritedOps extends Traverse.ToTraverseOps"),
        `object`("ops") `with` `implicit`("toAllTraverseOps", "[F, LP0] F[LP0] => Traverse[F] => Traverse.AllOps[F, LP0]"),
      )

    traverseObject mustBeExactly syntheticStructure
  }

  def testUnaryTypeConstructor(): Unit = {
    val text =
      s"""
         |import simulacrum._
         |
         |@typeclass trait Func${caret}tor[F[_]] {
         |  def map[A, B](fa: F[A])(f: A => B): F[B]
         |}
       """.stripMargin

    val functorObject = getSourceElement(text)

    val syntheticStructure =
      `object`("Functor")(
        applyMethod("Functor", "F"),
        `trait`("Ops[F[_], LP0]")(
          `def`("self", "F[LP0]"),
          `def`("map", "[B] (LP0 => B) => F[B]")
        ),
        `trait`("ToFunctorOps") `with` `implicit`("toFunctorOps", "[F, LP0] F[LP0] => Functor[F] => Functor.Ops[F, LP0]"),
        `trait`("AllOps[F[_], LP0] extends Functor.Ops[F, LP0]"),
        `object`("nonInheritedOps extends Functor.ToFunctorOps"),
        `object`("ops") `with` `implicit`("toAllFunctorOps", "[F, LP0] F[LP0] => Functor[F] => Functor.AllOps[F, LP0]"),
      )

    functorObject mustBeExactly syntheticStructure
  }

  def testBinaryTypeConstructor(): Unit = {
    val text =
      s"""
         |import simulacrum._
         |
         |@typeclass trait Str${caret}ong[F[_, _]] {
         |  def first[A, B, C](fab: F[A, B]): F[(A, C), (B, C)]
         |}
       """.stripMargin

    val strongObject = getSourceElement(text)

    val syntheticStructure =
      `object`("Strong")(
        applyMethod("Strong", "F"),
        `trait`("Ops[F[_, _], LP0, LP1]")(
          `def`("self", "F[LP0, LP1]"),
          `def`("first", "[C] F[(LP0, C), (LP1, C)]")
        ),
        `trait`("ToStrongOps") `with` `implicit`("toStrongOps", "[F, LP0, LP1] F[LP0, LP1] => Strong[F] => Strong.Ops[F, LP0, LP1]"),
        `trait`("AllOps[F[_, _], LP0, LP1] extends Strong.Ops[F, LP0, LP1]"),
        `object`("nonInheritedOps extends Strong.ToStrongOps"),
        `object`("ops") `with` `implicit`("toAllStrongOps", "[F, LP0, LP1] F[LP0, LP1] => Strong[F] => Strong.AllOps[F, LP0, LP1]"),
      )

    strongObject mustBeExactly syntheticStructure
  }

  def testTertiaryTypeConstructor(): Unit = {
    val text =
      s"""
         |import simulacrum._
         |
         |@typeclass trait Tri${caret}functor[F[_, _, _]] {
         |  def trimap[A, B, C, D, E, G](fabc: F[A, B, C])(f: A => D, g: B => E, h: C => G): F[D, E, G]
         |  @noop def first[A, B, C, D](fabc: F[A, B, C])(f: A => D): F[D, B, C] = trimap(fabc)(f, identity, identity)
         |  @noop def second[A, B, C, D](fabc: F[A, B, C])(f: B => D): F[A, D, C] = trimap(fabc)(identity, f, identity)
         |  @noop def third[A, B, C, D](fabc: F[A, B, C])(f: C => D): F[A, B, D] = trimap(fabc)(identity, identity, f)
         |}
       """.stripMargin

    val trifunctorObject = getSourceElement(text)

    val syntheticStructure =
      `object`("Trifunctor")(
        applyMethod("Trifunctor", "F"),
        `trait`("Ops[F[_, _, _], LP0, LP1, LP2]")(
          `def`("self", "F[LP0, LP1, LP2]"),
          `def`("trimap", "[D, E, G] (LP0 => D, LP1 => E, LP2 => G) => F[D, E, G]"),
        ),
        `trait`("ToTrifunctorOps") `with`
          `implicit`("toTrifunctorOps", "[F, LP0, LP1, LP2] F[LP0, LP1, LP2] => Trifunctor[F] => Trifunctor.Ops[F, LP0, LP1, LP2]"),
        `trait`("AllOps[F[_, _, _], LP0, LP1, LP2] extends Trifunctor.Ops[F, LP0, LP1, LP2]"),
        `object`("nonInheritedOps extends Trifunctor.ToTrifunctorOps"),
        `object`("ops") `with`
          `implicit`("toAllTrifunctorOps", "[F, LP0, LP1, LP2] F[LP0, LP1, LP2] => Trifunctor[F] => Trifunctor.AllOps[F, LP0, LP1, LP2]")
      )

    trifunctorObject mustBeExactly syntheticStructure
  }
}

package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class ImplicitConversionsResolveTest extends ScalaLightCodeInsightFixtureTestCase {
  def testSCL17570(): Unit = checkTextHasNoErrors(
    s"""
       |object A {
       | val l: Long = 1
       | val l2: java.lang.Long = 1
       |}
       |""".stripMargin
  )

  def testSCL20378(): Unit = checkTextHasNoErrors(
    """
      |import Conversions._
      |
      |object Test {
      |  def main(args: Array[String]): Unit = {
      |    println {
      |      1.convert[String](10.0)
      |    }
      |  }
      |}
      |
      |object Conversions {
      |
      |  implicit class GenericConversion2[A, B](x: A) {
      |    def convert[R](y: B)(implicit f: (A, B) => R): R = f(x, y)
      |  }
      |
      |  implicit val intToStringM: (Int, Double) => String = (x, y) => {
      |      (y + x).toString
      |    }
      |}
      |""".stripMargin
  )

  def testSCL15323(): Unit = checkTextHasNoErrors(
    """
      |object SelfTypeTests {
      |  trait Foo {
      |    def foo(): Int = 42
      |  }
      |
      |  object Foo {
      |    implicit class Ext(private val f: Foo) extends AnyVal {
      |      def fooExt(): Int = 23
      |    }
      |  }
      |
      |  trait Bar { self: Foo =>
      |    def bar(): Int = {
      |      self.foo()
      |      self.fooExt()
      |    }
      |  }
      |}
      |""".stripMargin
  )

  def testSCL22040(): Unit = checkTextHasNoErrors(
    """
      |object Example {
      |
      |  class Foo[A](val v: A)
      |
      |  class FooConverter[A] extends (A => Foo[A]) {
      |    override def apply(a: A): Foo[A] = new Foo[A](a)
      |  }
      |  implicit def toFooConverter[A]: FooConverter[A] = new FooConverter[A]
      |
      |  def fooValue[A](foo: Foo[A]): A = foo.v
      |
      |  private val x: Int = fooValue(1)
      |
      |}
      |""".stripMargin
  )
}


@Category(Array(classOf[TypecheckerTests]))
class ImplicitConversionsScala212ResolveTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_2_12

  override protected def additionalCompilerOptions: Seq[String] =
    Seq("-Xsource:3")

  /**
   * [[https://youtrack.jetbrains.com/issue/SCL-25850 SCL-25850]]
   * Package-object implicits remain in scope in Scala 2.12 with `-Xsource:3`.
   */
  def testSCL25850(): Unit = checkTextHasNoErrors(
    """
      |package mySbt {
      |  trait Scoped
      |
      |  final class TaskKey[A] extends Scoped
      |
      |  object Keys {
      |    val compile: TaskKey[Unit] = new TaskKey[Unit]
      |    val compilerReporter: TaskKey[Unit] = new TaskKey[Unit]
      |  }
      |
      |  trait SlashSyntax {
      |    implicit def mySbtSlashSyntaxRichScopeFromScoped(scope: Scoped): RichScope =
      |      new RichScope
      |  }
      |
      |  final class RichScope {
      |    def /[A](key: TaskKey[A]): TaskKey[A] = key
      |  }
      |}
      |
      |package object mySbt extends mySbt.SlashSyntax
      |
      |object Reproducer {
      |  import mySbt.Keys.compile
      |
      |  compile / mySbt.Keys.compilerReporter
      |}
      |""".stripMargin
  )
}


@Category(Array(classOf[TypecheckerTests]))
class ImplicitConversionsScala213ResolveTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_2_13

  def testReceiverConstrainsImplicitConversionContextBound(): Unit = checkTextHasNoErrors(
    """
      |class FooOps[F[_]] {
      |  def bar(f: F[Int]): Int = 123
      |}
      |
      |trait Monad[F[_]]
      |
      |import scala.language.implicitConversions
      |implicit def fooOps[F[_]: Monad](value: F[Int]): FooOps[F] = new FooOps[F]
      |
      |implicit val ml: Monad[List] = new Monad[List] {}
      |implicit val mo: Monad[Option] = new Monad[Option] {}
      |
      |Option(123).bar(Option(123))
      |""".stripMargin
  )

  // Scala 2 chooses a view before checking the selected member's arguments.
  // Unlike Scala 3, the argument to bar therefore cannot disambiguate F here.
  def testMemberArgumentDoesNotConstrainImplicitConversionContextBound(): Unit = checkHasErrorAroundCaret(
    s"""
       |class FooOps[F[_]] {
       |  def bar(f: F[Int]): Int = 123
       |}
       |
       |trait Monad[F[_]]
       |
       |import scala.language.implicitConversions
       |implicit def fooOps[A, F[_]: Monad](value: A): FooOps[F] = new FooOps[F]
       |
       |implicit val ml: Monad[List] = new Monad[List] {}
       |implicit val mo: Monad[Option] = new Monad[Option] {}
       |
       |123.ba${CARET}r(Option(123))
       |""".stripMargin
  )
}


trait ImplicitConversionsScala3SCL19475Tests {
  self: ScalaLightCodeInsightFixtureTestCase =>

  // SCL-19475: the ticket reproduction, kept verbatim.
  def testSCL19475TicketReproduction(): Unit = checkTextHasNoErrors(
    """
      |class FooOps[F[_]] {
      |  def bar(f: F[Int]): Int = 123
      |}
      |
      |trait Monad[F[_]]
      |
      |import scala.language.implicitConversions
      |implicit def fooOps[A, F[_]: Monad](a: A): FooOps[F] = new FooOps[F] {}
      |
      |implicit val ml: Monad[List] = new Monad[List] {}
      |implicit val mo: Monad[Option] = new Monad[Option] {}
      |
      |123.bar(Option(123))
      |""".stripMargin
  )

  def testMemberArgumentConstrainsExplicitImplicitConversionUsingClause(): Unit = checkTextHasNoErrors(
    """
      |class FooOps[T] {
      |  def bar(value: T): Int = 123
      |}
      |
      |trait Evidence[T]
      |
      |import scala.language.implicitConversions
      |implicit def fooOps[A, T](value: A)(using Evidence[T]): FooOps[T] = new FooOps[T]
      |
      |given Evidence[Int] = new Evidence[Int] {}
      |given Evidence[String] = new Evidence[String] {}
      |
      |123.bar("value")
      |""".stripMargin
  )

  def testSingletonBoundedMemberArgumentIsNotWidenedBeforeImplicitConversionUsingClause(): Unit = checkTextHasNoErrors(
    """
      |class FooOps[T] {
      |  def bar(value: T): Int = 123
      |}
      |
      |trait Evidence[T]
      |
      |import scala.language.implicitConversions
      |implicit def fooOps[A, T <: Singleton](value: A)(using Evidence[T]): FooOps[T] = new FooOps[T]
      |
      |given Evidence["value"] = new Evidence["value"] {}
      |
      |123.bar("value")
      |""".stripMargin
  )

  def testExpectedMemberResultConstrainsImplicitConversionContextBound(): Unit = checkTextHasNoErrors(
    """
      |class FooOps[T] {
      |  def result: T = ???
      |}
      |
      |trait Evidence[T]
      |
      |import scala.language.implicitConversions
      |implicit def fooOps[A, T: Evidence](value: A): FooOps[T] = new FooOps[T]
      |
      |given Evidence[Int] = new Evidence[Int] {}
      |given Evidence[String] = new Evidence[String] {}
      |
      |val result: String = 123.result
      |""".stripMargin
  )

  def testUnconstrainedImplicitConversionContextBoundRemainsAmbiguous(): Unit = checkHasErrorAroundCaret(
    s"""
       |class FooOps[F[_]] {
       |  def bar(value: Int): Int = 123
       |}
       |
       |trait Monad[F[_]]
       |
       |import scala.language.implicitConversions
       |implicit def fooOps[A, F[_]: Monad](value: A): FooOps[F] = new FooOps[F]
       |
       |implicit val listMonad: Monad[List] = new Monad[List] {}
       |implicit val optionMonad: Monad[Option] = new Monad[Option] {}
       |
       |123.ba${CARET}r(123)
       |""".stripMargin
  )
}


@Category(Array(classOf[TypecheckerTests]))
abstract class ImplicitConversionsScala3ResolveTestBase
  extends ScalaLightCodeInsightFixtureTestCase
    with ImplicitConversionsScala3SCL19475Tests {

  // Regression source: scala3@c69985cf44, tests/pos/i6914.scala.
  def testExpectedTypeConstrainsGenericConversionEvidence(): Unit = checkTextHasNoErrors(
    """
      |trait Expr[T]
      |trait Liftable[T]
      |
      |object test1 {
      |  class ToExpr[T](using Liftable[T]) extends Conversion[T, Expr[T]] {
      |    def apply(x: T): Expr[T] = ???
      |  }
      |  given toExpr[T](using Liftable[T]): ToExpr[T] = new ToExpr[T]
      |
      |  given Liftable[Int] = ???
      |  given Liftable[String] = ???
      |
      |  def x = summon[ToExpr[String]]
      |  def y = summon[Conversion[String, Expr[String]]]
      |
      |  def a: Expr[String] = "abc"
      |}
      |
      |object test2 {
      |  given autoToExpr[T](using Liftable[T]): Conversion[T, Expr[T]] with {
      |    def apply(x: T): Expr[T] = ???
      |  }
      |
      |  given Liftable[Int] = ???
      |  given Liftable[String] = ???
      |
      |  def a: Expr[String] = "abc"
      |}
      |""".stripMargin
  )

  // Regression source: scala3@233c8ec8e6, tests/pos/i15867.specs2.scala.
  def testInheritedConversionToNestedClassForOperatorSelection(): Unit = checkTextHasNoErrors(
    """
      |class Foo:
      |  given Conversion[String, Data] with
      |    def apply(str: String): Data = new Data(str)
      |
      |  class Data(str: String):
      |    def |(str: String) = new Data(this.str + str)
      |
      |class Bar extends Foo:
      |  "str" | "ing"
      |""".stripMargin
  )

  // Regression source: scala3@30f9f48257, tests/pos/typeclass-encoding.scala.
  // The second, implicit-only selection in that source is intentionally omitted:
  // the compiler test itself documents that expression as unsupported.
  def testConversionWithDependentResultFromImplicitEvidence(): Unit = checkTextHasNoErrors(
    """
      |object runtime {
      |  trait TypeClass {
      |    type This
      |    type StaticPart[This]
      |  }
      |
      |  trait Implementation[From] {
      |    type This = From
      |    type Implemented <: TypeClass
      |    def inject(x: From): Implemented { type This = From }
      |  }
      |
      |  class CompanionOf[T] { type StaticPart[_] }
      |
      |  def inst[From, To <: TypeClass](
      |    implicit ev1: Implementation[From] { type Implemented = To },
      |    ev2: CompanionOf[To]
      |  ): Implementation[From] { type Implemented = To } & ev2.StaticPart[From] =
      |    ev1.asInstanceOf
      |
      |  implicit def inject[From](x: From)(
      |    implicit ev1: Implementation[From]
      |  ): ev1.Implemented { type This = From } = ev1.inject(x)
      |}
      |
      |object semiGroups {
      |  import runtime.*
      |
      |  trait SemiGroup extends TypeClass {
      |    def add(that: This): This
      |  }
      |
      |  trait Monoid extends SemiGroup {
      |    type StaticPart[This] <: MonoidStatic[This]
      |  }
      |
      |  abstract class MonoidStatic[This] { def unit: This }
      |
      |  implicit def companionOfMonoid: CompanionOf[Monoid] {
      |    type StaticPart[X] = MonoidStatic[X]
      |  } = new CompanionOf[Monoid] {
      |    type StaticPart[X] = MonoidStatic[X]
      |  }
      |
      |  implicit object extend_Int_Monoid extends MonoidStatic[Int], Implementation[Int] {
      |    type Implemented = Monoid
      |    def unit: Int = 0
      |    def inject($this: Int) = new Monoid {
      |      type This = Int
      |      def add(that: This): This = $this + that
      |    }
      |  }
      |
      |  def sum[T](xs: List[T])(
      |    implicit evidence: Implementation[T] { type Implemented = Monoid }
      |  ) = xs.foldLeft(inst[T, Monoid].unit)((x, y) => inject(x) `add` y)
      |}
      |""".stripMargin
  )

  def testSCL21884(): Unit = checkTextHasNoErrors(
    """
      |trait Foo[A]
      |object Foo
      |
      |extension (foo: Foo.type) def derived[A] = ???
      |
      |case class Bar() derives Foo
      |""".stripMargin
  )


  def testSCL23230(): Unit = checkTextHasNoErrors(
    """
      |class Vec(val x: Double, val y: Double)
      |
      |trait Context[V]
      |
      |implicit class VecOps1[V](lhs: V)(using v: Context[V]):
      |  def foo1: Int = 0
      |
      |implicit class VecOps2[V](lhs: V)(implicit v: Context[V]):
      |  def foo2: Int = 0
      |
      |extension [V](lhs: V)(using v: Context[V])
      |  def foo3: Int = 0
      |
      |extension [V](lhs: V)
      |  def foo4(using v: Context[V]): Int = 0
      |
      |object Main:
      |  def main(args: Array[String]): Unit =
      |    val vec = new Vec(0, 1)
      |
      |    given Context[Vec] = ???
      |
      |    vec.foo1
      |    vec.foo2
      |    vec.foo3
      |    vec.foo4
      |""".stripMargin
  )

  def testSimpleLeadingUsingClause(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  trait X
      |  given X = ???
      |  given conversion(using X): Conversion[Int, String] = ???
      |  val s: String = 123
      |
      |  trait Foo
      |  implicit def int2Foo(using X)(i: Int): Foo = ???
      |  val f: Foo = 123
      |}
      |""".stripMargin
  )

  def testLeadingUsingDependentSubst(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  trait Foo { type X }
      |  given foo: Foo with { type X = Int }
      |  given Int = 123
      |  implicit def int22s(using f: Foo)(i: f.X)(using f.X): String = ???
      |  val x: String = 123
      |}
      |""".stripMargin
  )

  def testLeadingUsingTypeParamSubst(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |trait Bar[A]
      |  given bb: Bar[Int] = ???
      |  given Int = 123
      |  implicit def int2s[A](using Bar[A])(i: A)(using A): String = ???
      |  val s: String = 123
      |}
      |""".stripMargin
  )

  def testInterleavedUsingClause(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  trait F2
      |  trait B2[A]
      |  given F2 = new F2 {}
      |  given B2[String] = ???
      |  implicit def int2s[A](using F2)(i: Int)(using B2[A]): A = ???
      |  val s: String = 1
      |}
      |""".stripMargin
  )

  def testSCL23772(): Unit = checkTextHasNoErrors(
    """
      |trait Task[T]
      |
      |trait Init[ScopeType] {
      |  trait Initialize[T]
      |  object Initialize {
      |    implicit final class JoinInitSeq[T](s: Seq[Initialize[T]]) {
      |      def join: Initialize[Seq[T]] = ???
      |    }
      |  }
      |}
      |
      |object Def extends Init[String]
      |
      |object Scoped {
      |  trait ScopingSetting
      |
      |  implicit final class RichTaskSeq[T](keys: Seq[Def.Initialize[Task[T]]]) {
      |    def join: Def.Initialize[Task[Seq[T]]] = ???
      |  }
      |}
      |
      |object Example3 {
      |    val seq1: Seq[Def.Initialize[Task[Unit]] with Scoped.ScopingSetting] = ???
      |    val seq2: Seq[Def.Initialize[Task[Unit]]] = seq1
      |    val seq3: Seq[Scoped.ScopingSetting] = seq1
      |
      |    seq1.join // BAD
      |    seq2.join
      |}
      |
      |""".stripMargin
  )
}

@Category(Array(classOf[TypecheckerTests]))
class ImplicitConversionsScala33ResolveTest
  extends ImplicitConversionsScala3ResolveTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_3_3
}

class ImplicitConversionsScala3ResolveTest extends ImplicitConversionsScala3ResolveTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_7
}

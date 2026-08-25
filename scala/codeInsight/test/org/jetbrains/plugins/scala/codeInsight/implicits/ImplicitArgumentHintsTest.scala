package org.jetbrains.plugins.scala.codeInsight.implicits

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.junit.Assert.assertEquals

class ImplicitArgumentHintsTest extends ImplicitHintsTestBase {
  import Hint.{End => E, Start => S}

  //Scala 3 renders a compound type with `&` instead of `with`
  protected def compoundTypeText: String = "Bar with Serializable"

  def testSimpleImplicitArgument(): Unit = doTest(
    s"""
       |class A
       |object A {
       |  def fun()(implicit a: A): Unit = ???
       |}
       |implicit val implicitA: A = new A
       |A.fun()$S(implicitA)$E
     """.stripMargin
  )

  def testMissingImplicitArgument(): Unit = doTest(
    s"""
       |class A
       |object A {
       |  def fun()(implicit a: A): Unit = ???
       |}
       |
       |A.fun()$S(?: A)$E
     """.stripMargin
  )

  def testImplicitArgumentInGenerator(): Unit = doTest(
    s"""
      |class A
      |class B[X] {
      |  def foreach(f: X => Unit)(implicit a: A): Unit = ???
      |}
      |
      |implicit val implicitA: A = new A
      |for {
      |  x <-$S(implicitA)$E new B[Int]
      |} println(x)
    """.stripMargin
  )

  def testMissingImplicitArgumentInGenerator(): Unit = doTest(
    s"""
       |class A
       |class B[X] {
       |  def foreach(f: X => Unit)(implicit a: A): Unit = ???
       |}
       |
       |for {
       |  x <-$S(?: A)$E new B[Int]
       |} println(x)
    """.stripMargin
  )

  def testImplicitArgumentInGuard(): Unit = doTest(
    s"""
       |class A
       |class B[X] {
       |  def withFilter(f: X => Boolean)(implicit a: A): B[X] = ???
       |  def foreach(f: X => Unit): Unit = ???
       |}
       |
       |implicit val implicitA: A = new A
       |for {
       |  x <- new B[Int]
       |  if$S(implicitA)$E x > 0
       |} println(x)
    """.stripMargin
  )

  def testImplicitArgumentInForBinding(): Unit = doTest(
    s"""
       |class A
       |class B[X] {
       |  def foreach(f: X => Unit): Unit = ???
       |  def withFilter(f: X => Boolean): B[X] = ???
       |  def map[Y](f: X => Y)(implicit a: A): B[Y] = ???
       |}
       |
       |implicit val implicitA: A = new A
       |for {
       |  x <- new B[Int]
       |  y =$S(implicitA)$E  x
       |  if x > 0
       |} println(x)
    """.stripMargin
  )

  def testBothByNameAndImplicitScope(): Unit =
    doTest(
      s"""
         |object Foo {
         |  class A; class B; class C; class D
         |  implicit def aFromB(implicit b: B): A = new A
         |  implicit def bFromC(implicit c: C): B = new B
         |
         |  implicit val someD: D = new D
         |  def materializeB(implicit a: A, d: D): B = new B
         |  materializeB$S(aFromB(bFromC(?: C)), someD)$E
         |}""".stripMargin,
      expand = true
    )

  //SCL-14357
  def testClassTagMaterialized(): Unit = doTest(
    s"""
       |import scala.reflect.ClassTag
       |object Foo {
       |  def g[T: ClassTag](): Unit = ()
       |  g[Int]()$S(ClassTag.Int)$E
       |}""".stripMargin
  )

  //SCL-14357
  def testClassTagFromContextBoundEvidence(): Unit = doTest(
    s"""
       |import scala.reflect.ClassTag
       |object Foo {
       |  def g[T: ClassTag](): Unit = ()
       |  def f[T: ClassTag](): Unit = g[T]()$S(classTag$$T$$0)$E
       |}""".stripMargin
  )

  //SCL-14357
  def testClassTagFromExplicitImplicitParameter(): Unit = doTest(
    s"""
       |import scala.reflect.ClassTag
       |object Foo {
       |  def g[T: ClassTag](): Unit = ()
       |  def f[T](implicit tag: ClassTag[T]): Unit = g[T]()$S(tag)$E
       |}""".stripMargin
  )

  //SCL-14357
  def testClassTagFromExistingImplicitValue(): Unit = doTest(
    s"""
       |import scala.reflect.ClassTag
       |object Foo {
       |  def g[T: ClassTag](): Unit = ()
       |  implicit val tag: ClassTag[Int] = ClassTag.Int
       |  g[Int]()$S(tag)$E
       |}""".stripMargin
  )

  //SCL-14357
  def testManifestFromContextBoundEvidence(): Unit = doTest(
    s"""
       |object Foo {
       |  def g[T: Manifest](): Unit = ()
       |  def f[T: Manifest](): Unit = g[T]()$S(manifest$$T$$0)$E
       |}""".stripMargin
  )

  //SCL-14357, materialization must still happen when there is no value to refer to
  def testClassTagMaterializedForArbitraryClasses(): Unit = doTest(
    s"""
       |import scala.reflect.ClassTag
       |object Foo {
       |  class Bar
       |  class Baz[A]
       |  object Qux
       |  type Alias = Bar
       |
       |  def g[T: ClassTag](): Unit = ()
       |
       |  g[Bar]()$S(ClassTag(classOf[Bar]))$E
       |  g[Baz[Bar]]()$S(ClassTag(classOf[Baz[Bar]]))$E
       |  g[Qux.type]()$S(ClassTag(classOf[Qux.type]))$E
       |  g[Alias]()$S(ClassTag(classOf[Bar]))$E
       |  g[List[Bar]]()$S(ClassTag(classOf[List[Bar]]))$E
       |  g[Array[Bar]]()$S(ClassTag(classOf[Array[Bar]]))$E
       |  g[Bar with Serializable]()$S(ClassTag(classOf[$compoundTypeText]))$E
       |  g[(Bar, Int)]()$S(ClassTag(classOf[(Bar, Int)]))$E
       |  g[Bar => Int]()$S(ClassTag(classOf[Bar => Int]))$E
       |  g[Unit]()$S(ClassTag.Unit)$E
       |  g[Nothing]()$S(ClassTag.Nothing)$E
       |  g[Any]()$S(ClassTag.Any)$E
       |}""".stripMargin
  )

  //SCL-14357, an implicit of an unrelated type must not shadow materialization
  def testClassTagMaterializedDespiteUnrelatedImplicit(): Unit = doTest(
    s"""
       |import scala.reflect.ClassTag
       |object Foo {
       |  class Bar
       |  def g[T: ClassTag](): Unit = ()
       |  implicit val s: String = ""
       |  g[Bar]()$S(ClassTag(classOf[Bar]))$E
       |}""".stripMargin
  )

  //SCL-14357, a ClassTag of a different type argument must not be picked up
  def testClassTagMaterializedDespiteNonConformingClassTag(): Unit = doTest(
    s"""
       |import scala.reflect.ClassTag
       |object Foo {
       |  def g[T: ClassTag](): Unit = ()
       |  implicit val tag: ClassTag[String] = ClassTag(classOf[String])
       |  g[Int]()$S(ClassTag.Int)$E
       |}""".stripMargin
  )

  //SCL-14357, only implicit values are eligible
  def testClassTagMaterializedDespiteNonImplicitValue(): Unit = doTest(
    s"""
       |import scala.reflect.ClassTag
       |object Foo {
       |  def g[T: ClassTag](): Unit = ()
       |  val tag: ClassTag[Int] = ClassTag.Int
       |  g[Int]()$S(ClassTag.Int)$E
       |}""".stripMargin
  )

  //SCL-14357, an evidence parameter of an unrelated type parameter must not be picked up
  def testClassTagMaterializedDespiteEvidenceForOtherTypeParameter(): Unit = doTest(
    s"""
       |import scala.reflect.ClassTag
       |object Foo {
       |  class Bar
       |  def g[T: ClassTag](): Unit = ()
       |  def f[T: ClassTag](): Unit = g[Bar]()$S(ClassTag(classOf[Bar]))$E
       |}""".stripMargin
  )

  //SCL-14357
  def testManifestMaterializedForArbitraryClasses(): Unit = doTest(
    s"""
       |object Foo {
       |  class Bar
       |  def g[T: Manifest](): Unit = ()
       |  g[Bar]()$S(Manifest)$E
       |  g[List[Bar]]()$S(Manifest)$E
       |}""".stripMargin
  )

  //SCL-14357, a ClassTag cannot be materialized for an unbounded method type parameter
  def testClassTagNotAvailableForMethodTypeParameter(): Unit = doTest(
    s"""
       |import scala.reflect.ClassTag
       |object Foo {
       |  def g[T: ClassTag](): Unit = ()
       |  def h[T](): Unit = g[T]()$S(?: ClassTag[T])$E
       |}""".stripMargin
  )

  //SCL-14357, an upper bound does not make a ClassTag available either
  def testClassTagNotAvailableForBoundedMethodTypeParameter(): Unit = doTest(
    s"""
       |import scala.reflect.ClassTag
       |object Foo {
       |  def g[T: ClassTag](): Unit = ()
       |  def h[T <: AnyRef](): Unit = g[T]()$S(?: ClassTag[T])$E
       |}""".stripMargin
  )

  //SCL-14357
  def testClassTagNotAvailableForClassTypeParameter(): Unit = doTest(
    s"""
       |import scala.reflect.ClassTag
       |object Foo {
       |  def g[T: ClassTag](): Unit = ()
       |  class C[T] { def m(): Unit = g[T]()$S(?: ClassTag[T])$E }
       |}""".stripMargin
  )

  //SCL-14357
  def testClassTagNotAvailableForAbstractTypeMember(): Unit = doTest(
    s"""
       |import scala.reflect.ClassTag
       |object Foo {
       |  trait H { type T }
       |  def g[T: ClassTag](): Unit = ()
       |  def h(x: H): Unit = g[x.T]()$S(?: ClassTag[x.T])$E
       |}""".stripMargin
  )

  //SCL-14357
  def testClassTagNotAvailableForOwnAbstractTypeMember(): Unit = doTest(
    s"""
       |import scala.reflect.ClassTag
       |object Foo {
       |  def g[T: ClassTag](): Unit = ()
       |  class C { type T; def m(): Unit = g[T]()$S(?: ClassTag[T])$E }
       |}""".stripMargin
  )

  //SCL-14357
  def testManifestNotAvailableForMethodTypeParameter(): Unit = doTest(
    s"""
       |object Foo {
       |  def g[T: Manifest](): Unit = ()
       |  def h[T](): Unit = g[T]()$S(?: Manifest[T])$E
       |}""".stripMargin
  )

  //SCL-14357, NoManifest is always available, so an OptManifest is materialized even for an abstract type
  def testOptManifestAvailableForMethodTypeParameter(): Unit = doTest(
    s"""
       |import scala.reflect.OptManifest
       |object Foo {
       |  def g[T: OptManifest](): Unit = ()
       |  def h[T](): Unit = g[T]()$S(OptManifest)$E
       |}""".stripMargin
  )

  //SCL-14357, an abstract type is fine as long as there is an evidence parameter to refer to
  def testClassTagForClassTypeParameterWithContextBound(): Unit = doTest(
    s"""
       |import scala.reflect.ClassTag
       |object Foo {
       |  def g[T: ClassTag](): Unit = ()
       |  class C[T: ClassTag] { def m(): Unit = g[T]()$S(classTag$$T$$0)$E }
       |}""".stripMargin
  )

  //SCL-14358, types which have a predefined tag are shown as a reference to it
  def testPredefinedClassTags(): Unit = doTest(
    s"""
       |import scala.reflect.ClassTag
       |object Foo {
       |  def g[T: ClassTag](): Unit = ()
       |
       |  g[Byte]()$S(ClassTag.Byte)$E
       |  g[Short]()$S(ClassTag.Short)$E
       |  g[Char]()$S(ClassTag.Char)$E
       |  g[Int]()$S(ClassTag.Int)$E
       |  g[Long]()$S(ClassTag.Long)$E
       |  g[Float]()$S(ClassTag.Float)$E
       |  g[Double]()$S(ClassTag.Double)$E
       |  g[Boolean]()$S(ClassTag.Boolean)$E
       |  g[Unit]()$S(ClassTag.Unit)$E
       |  g[Any]()$S(ClassTag.Any)$E
       |  g[AnyVal]()$S(ClassTag.AnyVal)$E
       |  g[AnyRef]()$S(ClassTag.AnyRef)$E
       |  g[Nothing]()$S(ClassTag.Nothing)$E
       |  g[Null]()$S(ClassTag.Null)$E
       |  g[Object]()$S(ClassTag.Object)$E
       |}""".stripMargin
  )

  //SCL-14358, the type argument is shown, no matter whether it was explicit or inferred
  def testClassTagForInferredTypeArgument(): Unit = doTest(
    s"""
       |import scala.reflect.ClassTag
       |object Foo {
       |  class Bar
       |  def g[T: ClassTag](p: T): Unit = ()
       |
       |  g(new Bar)$S(ClassTag(classOf[Bar]))$E
       |  g(42)$S(ClassTag.Int)$E
       |  g("a")$S(ClassTag(classOf[String]))$E
       |}""".stripMargin
  )

  //SCL-14358, `.apply` is only shown when the corresponding hint is enabled, which it isn't by default
  def testClassTagApplyIsNotShownByDefault(): Unit = doTest(
    s"""
       |import scala.reflect.ClassTag
       |object Foo {
       |  class Bar
       |  def g[T: ClassTag](): Unit = ()
       |  g[Bar]()$S(ClassTag(classOf[Bar]))$E
       |}""".stripMargin
  )

  //SCL-14358, every part of the presentation resolves
  def testMaterializedClassTagNavigation(): Unit = doNavigationTest(
    """
      |import scala.reflect.ClassTag
      |object Foo {
      |  def g[T: ClassTag](): Unit = ()
      |  g[List[String]]()
      |}""".stripMargin,
    "(ClassTag{scala.reflect.ClassTag}" +
      "(classOf{scala.Predef.classOf}" +
      "[List{scala.collection.immutable.List}[String{java.lang.String}]]))"
  )

  //SCL-14358, a predefined tag resolves to the value in the `ClassTag` object
  def testPredefinedClassTagNavigation(): Unit = doNavigationTest(
    """
      |import scala.reflect.ClassTag
      |object Foo {
      |  def g[T: ClassTag](): Unit = ()
      |  g[Int]()
      |}""".stripMargin,
    "(ClassTag{scala.reflect.ClassTag}.Int{scala.reflect.ClassTag.Int})"
  )

}

class ImplicitArgumentHintsTestScala3 extends ImplicitArgumentHintsTest {
  import Hint.{End => E, Start => S}

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

  override protected def compoundTypeText: String = "Bar & Serializable"

  def testMultipleUsingClausesTrailing(): Unit = {
    doTest(
      s"""
         |object A {
         |  trait A; trait B; trait C
         |  given A = ???
         |  given B = ???
         |  given C = ???
         |  def foo(x: Int)(using a: A)(using b: B)(using C): Int = 123
         |  foo(1)$S(given_A)$E$S(given_B)$E$S(given_C)$E
         |}
         |""".stripMargin
    )
  }

  def testMultipleUsingClausesLeading(): Unit = {
    doTest(
      s"""
         |object A {
         |  trait A; trait B; trait C
         |  given A = ???
         |  given B = ???
         |  given C = ???
         |  def foo(using a: A)(using b: B)(using C)(x: Int): Int = 123
         |  foo$S(given_A)$E$S(given_B)$E$S(given_C)$E(1)
         |}
         |""".stripMargin
    )
  }

  def testMultipleUsingClausesInterleaving(): Unit = {
    doTest(
      s"""
         |object A {
         |  trait A; trait B; trait C; trait D;
         |  given A = ???
         |  given B = ???
         |  given C = ???
         |  given D = ???
         |  def foo(using a: A)(s: String)(using b: B)(using C)(x: Int)(using D): Int = 123
         |  foo$S(given_A)$E("foo")$S(given_B)$E$S(given_C)$E(1)$S(given_D)$E
         |}
         |""".stripMargin
    )
  }


  def testMultipleUsingClausesInterleavingMissing(): Unit = {
    doTest(
      s"""
         |object A {
         |  trait A; trait B; trait C; trait D;
         |  given A = ???
         |  given D = ???
         |  def foo(using a: A)(s: String)(using b: B)(using C)(x: Int)(using D): Int = 123
         |  foo$S(given_A)$E("foo")$S(?: B)$E$S(?: C)$E(1)$S(given_D)$E
         |}
         |""".stripMargin
    )
  }

  def testUsingClauseAfterInterleavedTypeClause(): Unit = {
    doTest(
      s"""
         |object A {
         |  trait Ctx
         |  given Ctx = ???
         |  def foo[A](first: A)[B](second: B)(using Ctx): B = second
         |  foo[Int](1)[String]("text")$S(given_Ctx)$E
         |}
         |""".stripMargin
    )
  }

  def testLeadingUsingClauseBeforeInterleavedTypeClause(): Unit = {
    doTest(
      s"""
         |object A {
         |  trait Ctx
         |  given Ctx = ???
         |  def foo(using Ctx)[A](value: A): A = value
         |  foo$S(given_Ctx)$E[String]("text")
         |}
         |""".stripMargin
    )
  }

  def testLeadingUsingClauseAfterInitialTypeClause(): Unit = {
    doTest(
      s"""
         |object A {
         |  trait Ctx
         |  given Ctx = ???
         |  def foo[A](using Ctx)(value: A): A = value
         |  foo[String]$S(given_Ctx)$E("text")
         |}
         |""".stripMargin
    )
  }

  def testLeadingUsingClauseBeforeSecondInterleavedTypeClause(): Unit = {
    doTest(
      s"""
         |object A {
         |  trait Ctx
         |  given Ctx = ???
         |  def foo[A](first: A)(using Ctx)[B](second: B): B = second
         |  foo[Int](1)$S(given_Ctx)$E[String]("text")
         |}
         |""".stripMargin
    )
  }

  // SCL-23860: for an underspecified expected type the compiler refuses the search
  // ("No implicit search was attempted ... not specific enough"). The inlay hint must use that
  // wording in its error tooltip instead of the generic "No implicits found for parameter".
  def testNoSearchAttemptedTooltipForUnderspecifiedExpectedType(): Unit = {
    val tooltips = errorTooltips(
      s"""
         |trait Mode[X[_]]
         |
         |def fallible[F[_], M >: Mode[F]](using M): (F[Int], M) = null
         |
         |val result = fallible
         |""".stripMargin
    )

    // the same tooltip is attached to both the collapsed and the expanded presentation
    assertEquals(
      Seq(ScalaCodeInsightBundle.message("no.implicit.search.was.attempted.for.parameter", "x$1: M")),
      tooltips.distinct
    )
  }

  // ...while an ordinary missing given still reports "No implicits found for parameter".
  def testNotFoundTooltipForSpecificExpectedType(): Unit = {
    val tooltips = errorTooltips(
      s"""
         |trait Mode[F[+x]]
         |
         |def fallible(using Mode[Option]): Unit = ???
         |
         |val result = fallible
         |""".stripMargin
    )

    assertEquals(
      Seq(ScalaCodeInsightBundle.message("no.implicits.found.for.parameter", "x$1: Mode[Option]")),
      tooltips.distinct
    )
  }
}

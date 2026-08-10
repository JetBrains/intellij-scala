package org.jetbrains.plugins.scala.codeInsight.implicits

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.junit.Assert.assertEquals

class ImplicitArgumentHintsTest extends ImplicitHintsTestBase {
  import Hint.{End => E, Start => S}

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

}

class ImplicitArgumentHintsTestScala3 extends ImplicitArgumentHintsTest {
  import Hint.{End => E, Start => S}

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

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

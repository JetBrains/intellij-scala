package org.jetbrains.plugins.scala.codeInsight.implicits

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

  def testMultipleUsingClausesTrailing(): Unit = {
    doTest(
      s"""
         |object A {
         |  trait A; trait B; trait C
         |  given A = ???
         |  given B = ???
         |  given C = ???
         |  def foo(x: Int)(using a: A)(using b: B)(using C): Int = 123
         |  foo(1)$S(given_A)(given_B)(given_C)$E
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
         |  foo$S(given_A)(given_B)(given_C)$E(1)
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
         |  foo$S(given_A)$E("foo")$S(given_B)(given_C)$E(1)$S(given_D)$E
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
         |  foo$S(given_A)$E("foo")$S(?: B)(?: C)$E(1)$S(given_D)$E
         |}
         |""".stripMargin
    )
  }
}

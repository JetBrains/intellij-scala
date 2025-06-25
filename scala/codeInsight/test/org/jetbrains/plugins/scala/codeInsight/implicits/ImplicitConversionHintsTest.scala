package org.jetbrains.plugins.scala.codeInsight.implicits

class ImplicitConversionHintsTest extends ImplicitHintsTestBase {
  import Hint.{End => E, Start => S}

  def testImplicitConversionFunction(): Unit = doTest(
    s"""
       |class A
       |class B
       |
       |implicit def AtoB(a: A): B = ???
       |
       |val b: B = ${S}AtoB(${E}new A$S)$E
     """.stripMargin
  )

  def testImplicitConversionClassExtendingTarget(): Unit = doTest(
    s"""
       |class A
       |class B
       |implicit class AtoB(a: A) extends B
       |
       |val b: B = ${S}AtoB(${E}new A$S)$E
    """.stripMargin
  )

  def testImplicitConversionClassProvidingMethod(): Unit = doTest(
    s"""
       |class A
       |implicit class AExt(a: A) {
       |  def test(): Unit = ???
       |}
       |
       |${S}AExt(${E}new A()$S)$E.test()
    """.stripMargin
  )

  //noinspection RedundantBlock
  //SCL-16335
  def testImplicitConversionsChain(): Unit = doTest(
    s"""implicit class StringOps(private val str: String) extends AnyVal {
      |  def foo(): Long = 1
      |}
      |
      |implicit class LongOps(private val long: Long) extends AnyVal {
      |  def bar(): Float = 2.0f
      |}
      |
      |implicit class FloatOps(private val float: Float) extends AnyVal {
      |  def baz(): String = "3"
      |}
      |
      |//code: "42".foo().bar().baz()
      |${S}FloatOps(${E}${S}LongOps(${E}${S}StringOps(${E}"42"${S})${E}.foo()${S})${E}.bar()${S})${E}.baz()
      |""".stripMargin
  )


  def testImplicitConversionInGenerator(): Unit = doTest(
    s"""
       |class A[X]
       |implicit class ProvideForeach[X](a: A[X]) {
       |  def foreach(f: X => Unit): Unit = ???
       |}
       |
       |for {
       |  x <- ${S}ProvideForeach(${E}new A[Int]$S)$E
       |} println(x)
    """.stripMargin
  )

  def testImplicitConversionInInnerGenerator(): Unit = doTest(
    s"""
       |class A[X]
       |implicit class ProvideForeach[X](a: A[X]) {
       |  def foreach(f: X => Unit): Unit = ???
       |}
       |
       |for {
       |  y <- Seq(1)
       |  x <- ${S}ProvideForeach(${E}new A[Int]$S)$E
       |} println(x)
    """.stripMargin
  )

  def testImplicitConversionInGeneratorProvidingWithFilter(): Unit = doTest(
    s"""
       |class A[X]
       |implicit class ProvideWithFilter[X](a: A[X]) {
       |  def withFilter(f: X => Boolean): Seq[X] = ???
       |}
       |
       |for {
       |  x <- ${S}ProvideWithFilter(${E}new A[Int]$S)$E
       |  if x > 0
       |} println(x)
    """.stripMargin
  )

  def testImplicitConversionForGuardResult(): Unit = doTest(
    s"""
       |class A[X] {
       |  def withFilter(f: X => Boolean): A[X] = ???
       |}
       |implicit class ProvideForeach[X](a: A[X]) {
       |  def foreach(f: X => Unit): Unit = ???
       |}
       |
       |for {
       |  x <- new A[Int]
       |  ${S}ProvideForeach(${E}if x > 0$S)$E
       |} println(x)
    """.stripMargin
  )

  def testImplicitConversionForMapResult(): Unit = doTest(
    s"""
       |class A[X] {
       |  def foreach(f: X => Unit): Unit = ???
       |  def map[Y](f: X => Y): A[Y] = ???
       |}
       |implicit class ProvideWithFilter[X](a: A[X]) {
       |  def withFilter(f: X => Boolean): A[X] = ???
       |}
       |
       |for {
       |  x <- new A[Int]
       |  ${S}ProvideWithFilter(${E}b = x$S)$E
       |  if b > 0
       |} println(x)
    """.stripMargin
  )

  def testImplicitConversionAroundForExpr(): Unit = doTest(
    s"""
       |class A[X] {
       |  def map[Y](f: X => Y): A[Y] = ???
       |}
       |class B
       |implicit class AtoB[X](a: A[X]) extends B
       |
       |val y: B = ${S}AtoB(${E}for {
       |  x <- new A[Int]
       |} yield x$S)$E
    """.stripMargin
  )

  def testMultipleUsingClausesTrailing(): Unit = {
    doTest(
      s"""
         |object A {
         |  trait A; trait B; trait C
         |  given A = ???
         |  given B = ???
         |  given C = ???
         |  implicit def foo(x: Int)(using a: A)(using b: B)(using C): String = ???
         |  val x: String = {$S}foo(${E}1$S)(given_A)(given_B)(given_C)$E
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
         |  implicit def foo(using a: A)(using b: B)(using C)(x: Int): String = ???
         |  val s: String = ${S}foo(given_A)(given_B)(given_C)(${E}1$S)$E
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
         |  def foo(using a: A)(s: String)(using b: B)(using C)(using D): String = ???
         |  val s: String = foo$S(given_A)(${E}1$S)(given_B)(given_C)(given_D)$E
         |}
         |""".stripMargin
    )
  }

  def testMultipleUsingClausesNested(): Unit = {
    doTest(
      s"""
         |object A {
         |  trait A; trait B; trait C; trait D
         |  given A = ???
         |  given A => B = ???
         |  given B => C = ???
         |  given B => D = ???
         |  def foo(using B)(i: Int)(using C, D): String = ???
         |  val s: String = ${S}foo(given_B(given_A)(${E}1${S})(given_C(given_B(given_A)), given_D(given_B(given_A)))${E}
         |}
         |""".stripMargin
    )
  }
}

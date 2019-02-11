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
    """
      |class A
      |implicit class AExt(a: A) {
      |  def test(): Unit = ???
      |}
      |
      |${S}AExt(${E}new A$S)$E.test()
    """.stripMargin
  )

  // TODO: add more tests regarding implicit conversion hints (but not regarding for-comprehensions)

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
}

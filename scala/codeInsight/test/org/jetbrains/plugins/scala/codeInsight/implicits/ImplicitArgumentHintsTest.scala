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
}

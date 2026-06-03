package org.jetbrains.plugins.scala.annotator

class ContravarianceTest extends ScalaHighlightingTestBase {

  //SCL-4123
  def testScl4123(): Unit = {
    assertNoErrors(
      s"""object Test {
         |  class A
         |  class C
         |  class B extends C
         |
         |  class Z[-T] //in case of covariant or invariant, all is ok
         |
         |  def goo[A, BB >: A](x: A): Z[BB] = new Z[BB]
         |  val zzzzzz = goo(new B) //here type is Z[Any], according to the compiler it's Z[B]
         |  zzzzzz
         |}
         |""".stripMargin
    )
  }

  //SCL-10110
  def testScl10110(): Unit = {
    assertNoErrors(
      s"""import scala.collection.immutable
         |
         |object Error {
         |
         |  class Foo[T](x: T)
         |
         |  class LolArray[T](val arr: Array[Foo[T]])
         |
         |  class LolImmutableHashMap[T](val arr: immutable.HashMap[Int, Foo[T]])
         |
         |  //Full example with various collections in corresponded ticket
         |  def main(args: Array[String]) {
         |    val lolArray = new LolArray(Array(new Foo(1))) // false error ( Array invariant )
         |    val lolImmutableHashMap = new LolImmutableHashMap(immutable.HashMap(1 -> new Foo(1))) // works ( mutable.HashMap covariant )
         |
         |    //    val lolArrayExplicit1 = new LolArray[Int](Array(new Foo(1))) // works
         |    //    val lolArrayExplicit2 = new LolArray(Array[Foo[Int]](new Foo(1))) // works
         |  }
         |}
       """.stripMargin)
  }

  //SCL-10238
  def testScl10238(): Unit = assertNoErrors(
    s"""class Foo[A](
       |    superfoos: Seq[Foo[_ >: A]],
       |    foos: Seq[Foo[A]],
       |    underfoos: Seq[Foo[_ <: A]]
       |)
       |
       |class Bar[A](
       |    superbars: Seq[Bar[_ >: A]],
       |    bars: Seq[Bar[A]],
       |    underbars: Seq[Bar[_ <: A]]
       |) extends Foo[A](
       |  superbars, // Type mismatch, expected: Seq[Foo[_ >: A]], actual: Seq[Bar[_ >: A]]
       |  bars, // Type mismatch, expected: Seq[Foo[A]], actual: Seq[Bar[A]]
       |  underbars // Type mismatch, expected: Seq[Foo[_ <: A]], actual: Seq[Bar[_ <: A]]
       |)
       |""".stripMargin
  )

  def testScl10238a(): Unit = assertNoErrors(
    s"""class Foo[A](superfoos: Seq[Foo[_ >: A]])
       |class Bar[A](superbars: Seq[Bar[_ >: A]]) extends Foo[A](superbars)
       |""".stripMargin
  )

  def testScl10238b(): Unit = assertNoErrors(
    s"""class Foo[A](foos: Seq[Foo[A]])
       |class Bar[A](bars: Seq[Bar[A]]) extends Foo[A](bars)
       |//Seq[Foo[A]]
       |""".stripMargin
  )

  def testScl10238c(): Unit = assertNoErrors(
    s"""class Foo[A](underfoos: Seq[Foo[_ <: A]])
       |class Bar[A](underbars: Seq[Bar[_ <: A]]) extends Foo[A](underbars)
       |//Seq[Foo[_ <: A]]
       |""".stripMargin
  )
}

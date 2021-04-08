package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
  * @author Nikolay.Tropin
  */
class ContravarianceTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  def testScl4123(): Unit = {
    val text =
      s"""object Test {
        |  class A
        |  class C
        |  class B extends C
        |
        |  class Z[-T] //in case of covariant or invariant, all is ok
        |
        |  def goo[A, BB >: A](x: A): Z[BB] = new Z[BB]
        |  val zzzzzz = goo(new B) //here type is Z[Any], according to the compiler it's Z[B]
        |  ${START}zzzzzz$END
        |}
        |
        |//Test.Z[B]""".stripMargin
    doTest(text)
  }

  def testSCL10110(): Unit ={
    doTest(
      s"""
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
         |    val lolArray = new LolArray(${START}Array(new Foo(1))$END) // false error ( Array invariant )
         |    val lolImmutableHashMap = new LolImmutableHashMap(immutable.HashMap(1 -> new Foo(1))) // works ( mutable.HashMap covariant )
         |
         |    //    val lolArrayExplicit1 = new LolArray[Int](Array(new Foo(1))) // works
         |    //    val lolArrayExplicit2 = new LolArray(Array[Foo[Int]](new Foo(1))) // works
         |  }
         |}
         |
         |//Array[Error.Foo[NotInferedT]]
       """.stripMargin)
  }

  def testSCL10238a(): Unit ={
    doTest(
      s"""
         |class Foo[A](superfoos: Seq[Foo[_ >: A]])         |
         |class Bar[A](superbars: Seq[Bar[_ >: A]]) extends Foo[A](${START}superbars$END)
         |//Seq[Foo[_ >: A]]
       """.stripMargin)
  }

  def testSCL10238b(): Unit ={
    doTest(
      s"""
         |class Foo[A](foos: Seq[Foo[A]])         |
         |class Bar[A](bars: Seq[Bar[A]]) extends Foo[A](${START}bars$END)
         |//Seq[Foo[A]]
       """.stripMargin)
  }

  def testSCL10238c(): Unit ={
    doTest(
      s"""
         |class Foo[A](underfoos: Seq[Foo[_ <: A]])         |
         |class Bar[A](underbars: Seq[Bar[_ <: A]]) extends Foo[A](${START}underbars$END)
         |//Seq[Foo[_ <: A]]
       """.stripMargin)
  }
}

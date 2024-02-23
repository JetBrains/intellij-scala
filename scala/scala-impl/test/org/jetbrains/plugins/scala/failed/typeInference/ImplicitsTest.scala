package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

class ImplicitsTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL9076(): Unit = doTest()

  def testSCL9961(): Unit = doTest()

  def testSCL3987(): Unit = doTest()


  def testSCL8831(): Unit = doTest()

  def testSCL9903(): Unit = doTest {
    s"""trait Prop extends  {
       |  def foo(s: String): Prop = ???
       |}
       |
      |object Prop {
       |  implicit def propBoolean(b: Boolean): Prop = ???
       |
      |  implicit def BooleanOperators(b: => Boolean): ExtendedBoolean = ???
       |
      |  class ExtendedBoolean(b: => Boolean) {
       |    def foo(s: String): Prop = ???
       |  }
       |}
       |
      |import Prop._
       |
      |val x = ${START}true.foo("aaa")$END
       |//Prop
    """.stripMargin
  }

  //SCL-7468
  def testSCL7468(): Unit = {
    doTest(
      s"""
         |class Container[A](x: A) { def value: A = x }
         |trait Unboxer[A, B] { def unbox(x: A): B }
         |trait LowPriorityUnboxer {
         |  implicit def defaultCase[A, B](implicit fun: A => B) = new Unboxer[A, B] { def unbox(x: A) = fun(x) }
         |}
         |object Unboxer extends LowPriorityUnboxer {
         |  def unbox[A, B](x: A)(implicit f: Unboxer[A, B]) = f.unbox(x)
         |  implicit def containerCase[A] = new Unboxer[Container[A], A] { def unbox(x: Container[A]) = x.value }
         |}
         |implicit def getContained[A](cont: Container[A]): A = cont.value
         |def container[A] = new Impl[A]
         |
         |class Impl[A] { def apply[B](x: => B)(implicit unboxer: Unboxer[B, A]): Container[A] = new Container(Unboxer.unbox(x)) }
         |
         |val stringCont = container("SomeString")
         |val a1 = ${START}stringCont$END
         |//Container[String]
      """.stripMargin,
      failIfNoAnnotatorErrorsInFileIfTestIsSupposedToFail = false //no annotator errros, just a wrong type inferred
    )
  }

  def testSCL8214(): Unit = {
    doTest(
      s"""
         |class A
         |
        |class Z[T]
         |class F[+T]
         |
        |class B
         |class C extends B
         |
        |implicit val z: Z[C] = new Z
         |implicit def r[S, T](p: S)(implicit x: Z[T]): F[T] = new F[T]
         |val r: F[B] = ${START}new A$END
         |//F[B]
      """.stripMargin)
  }

  def testSCL12180(): Unit = {
    doTest(
      s"""
         |case class Foo(a: Long, b: Int)
         |
         |implicit class FooOps(val self: Foo.type) {
         |  def apply(i: Int): Foo = {
         |    Foo(i.toLong, i)
         |  }
         |}
         |
         |Foo.apply(${START}1$END)
         |//Int
      """.stripMargin)
  }
}

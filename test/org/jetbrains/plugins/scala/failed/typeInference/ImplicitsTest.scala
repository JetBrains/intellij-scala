package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Alefas
  * @since 21/03/16
  */

@Category(Array(classOf[PerfCycleTests]))
class ImplicitsTest extends TypeInferenceTestBase {
  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL7955(): Unit = doTest()

  def testSCL8242(): Unit = doTest()

  def testSCL9076(): Unit = doTest()

  def testSCL9525(): Unit = doTest()

  def testSCL9961(): Unit = doTest()

  def testSCL3987(): Unit = doTest()

  def testSCL7605(): Unit = doTest()

  def testSCL8831(): Unit = doTest()

  def testSCL9302(): Unit = doTest {
    """
      |object SCL9302 {
      |
      |  class User
      |
      |  implicit class RichUser(user: User) {
      |    def hello(): Int = 1
      |  }
      |
      |  val user = new User
      |  user.hello()
      |
      |  trait UserTrait {
      |    this: User =>
      |
      |    /*start*/this.hello()/*end*/
      |  }
      |}
      |//Int
    """.stripMargin.trim
  }

  def testSCL7809(): Unit = doTest {
    """
      |class SCL7809 {
      |  implicit def longToString(s: Long): String = s.toString
      |  def useString(s: String) = s
      |  def useString(d: Boolean) = d
      |  /*start*/useString(1)/*end*/
      |}
      |//String
    """.stripMargin.trim
  }

  def testSCL7658(): Unit = {
    doTest(
      """implicit def i2s(i: Int): String = i.toString
        |
        |def hoo(x: String): String = {
        |  println(1)
        |  x
        |}
        |def hoo(x: Int): Int = {
        |  println(2)
        |  x
        |}
        |
        |val ss: String = hoo(/*start*/1/*end*/)
        |//Int
      """.stripMargin)
  }

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

  def testSCL10077(): Unit = {
    doTest(
      """
        |object SCL10077{
        |
        |  trait C[A] {
        |    def test(a: A): String
        |  }
        |  case class Ev[TC[_], A](a: A)(implicit val ev: TC[A]) {
        |    def operate(fn: (A, TC[A]) => Int): Int = 23
        |  }
        |  class A
        |  implicit object AisCISH extends C[A] {
        |    def test(a: A) = "A"
        |  }
        |
        |  val m: Map[String, Ev[C, _]] = Map.empty
        |  val r = m + ("mutt" -> Ev(new A))
        |  val x = r("mutt")
        |
        |  x.operate(/*start*/(arg, tc) => 66/*end*/)
        |}
        |
        |//(_$1, _$1[_$1]) => Int
      """.stripMargin)
  }

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
         |//String
      """.stripMargin)
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

  def testSCL6372(): Unit = {
    doTest(
      s"""
         |class TagA[A]
         |  class TagB[B]
         |
         |  abstract class Converter[A: TagA, B: TagB] {
         |    def work(orig: A): B
         |  }
         |  object Converter {
         |    class Detected[A: TagA, B: TagB] {
         |      def using(fun: A => B) = new Converter[A, B] {
         |        def work(orig: A) = fun(orig)
         |      }
         |    }
         |    def apply[A: TagA, B: TagB]() = new Detected
         |  }
         |
         |  class Config[A, B] {
         |    implicit val tagA = new TagA[A]
         |    implicit val tagB = new TagB[B]
         |  }
         |
         |  class Test extends Config[Long, Int] {
         |    val conv = Converter().using(java.lang.Long.bitCount)
         |    def run() = conv.work(${START}366111312291L$END)
         |  }
         |//Nothing
      """.stripMargin)
  }

  def testSCL10141(): Unit = {
    doTest(
      s"""
         |case class BuildInfoResult(identifier: String, value: Any, typeExpr: Any)
         |
         |object BuildInfo {
         |
         |  case class BuildInfoTask() {
         |
         |    def entry[A](info: BuildInfoKey.Entry[A]): Option[BuildInfoResult] = {
         |      val typeExpr: Any = ???
         |      val result: Option[(String, A)] = info match {
         |        case BuildInfoKey.Mapped(from, fun) =>
         |        ${START}fun$END
         |        entry(from).map { r => fun(r.identifier -> r.value.asInstanceOf[A]) }
         |      }
         |      result.map(r => BuildInfoResult(r._1, r._2, typeExpr))
         |    }
         |  }
         |}
         |
         |object BuildInfoKey {
         |  case class Mapped[A, B](from: Entry[A], fun: ((String, A)) => (String, B))(implicit val manifest: Manifest[B]) extends Entry[B]
         |  sealed trait Entry[A] { def manifest: Manifest[A] }
         |}
         |//((String, A)) => (String, A)
      """.stripMargin)
  }
}

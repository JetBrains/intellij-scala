package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class ExistentialConformanceTest extends TypeConformanceTestBase {
  def testSCL9402(): Unit = {
    val text =
      """import scala.language.existentials
        |
        |class Network {
        |  class Member(val name: String)
        |
        |  def join(name: String): Member = ???
        |}
        |
        |type NetworkMember = n.Member forSome {val n: Network}
        |
        |val chatter = new Network
        |
        |val fred: chatter.Member = chatter.join("Fred")
        |
        |val x: NetworkMember = fred
        |//True""".stripMargin
    doTest(text)
  }

  def testSCL7602(): Unit = {
    doApplicatonConformanceTest(
      s"""
        |class A[P[T]]{
        |  def use[T](p: P[T]) = ()
        |  def get: P[_] = ???
        |  use$caretMarker(get) //error
        |}
      """.stripMargin)
  }

  def testSCL10753a(): Unit = {
    doTest(
      s"""
         |import scala.language.higherKinds
         |
         |  class Holder[T[_]] {
         |    // a trait to hold a parametrized type T
         |    def t: T[Any] = null.asInstanceOf[T[Any]]
         |  }
         |
         |  class X[T] {
         |
         |    // a method that extract the parametrized type, when it is so.
         |    def foo[Q, X[_]](implicit x: X[Q] =:= T): Holder[X] = {
         |      new Holder[X]
         |    }
         |
         |  }
         |
         |  val o = new X[Seq[String]]
         |  val oVal = o.foo // REPL infers type Holder[Seq]; IDE infers type Holder[Nothing]
         |  val test: Holder[Seq] = oVal
         |//True""".stripMargin)
  }

  def testSCL10753b(): Unit = {
    doTest(
      s"""
         |  import scala.language.higherKinds
         |
         |  class Holder[T[_]] {
         |    // a trait to hold a parametrized type T
         |    def t: T[Any] = null.asInstanceOf[T[Any]]
         |  }
         |
         |  class X[T] {
         |
         |    // a method that extract the parametrized type, when it is so.
         |    def foo[Q, X[_]](implicit x: X[Q] =:= T): Holder[X] = {
         |      new Holder[X]
         |    }
         |
         |  }
         |
         |  val o = new X[Seq[String]]
         |           |
         |  val ot = o.foo.t // REPL infers type Seq[Any]; IDE infers type Nothing[Any]
         |  val test2: Seq[Any] = ot
         |//True""".stripMargin)
  }
}

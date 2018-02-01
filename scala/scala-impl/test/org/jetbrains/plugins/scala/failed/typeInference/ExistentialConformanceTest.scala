package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class ExistentialConformanceTest extends TypeConformanceTestBase {

  override protected def shouldPass: Boolean = false

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
}

package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class SCL20133Test extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL20133(): Unit = checkTextHasNoErrors(
    """
      |object Test {
      |  trait BF[-E, +To]
      |
      |  trait Comp[K[_]]
      |
      |  trait Col[A] {
      |    def head: A
      |    def to[CC[_]](bf: BF[A, CC[A]]): CC[A]
      |  }
      |  object ColComp extends Comp[Col]
      |
      |  implicit def co[A, CC[X] <: Col[X]](c: Comp[CC]): BF[A, CC[A]] = ???
      |
      |  def t(c: Col[Int]) = c.to(co(ColComp)) // was: type mismatch in IntelliJ
      |
      |  def t1(c: Col[Int]): Col[Int] = c.to(ColComp)
      |  def t2(c: Col[Int]) = c.to(ColComp)
      |  def t3(c: Col[Int]) = c.to(co(ColComp): BF[Int, Col[Int]])
      |
      |  t1(???).head
      |  t2(???).head
      |}
      |""".stripMargin
  )
}

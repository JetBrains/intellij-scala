package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class SCL20133Test extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL20133(): Unit = checkTextHasNoErrors(
    """
      |object Test {
      |  trait BF[-E, +To]
      |
      |  trait Comp[+K[_]]
      |
      |  trait Col[A] {
      |    def head: A
      |    def to[CC[_]](bf: BF[A, CC[A]]): CC[A]
      |  }
      |  object ColComp extends Comp[Col]
      |
      |  implicit def co[A, CC[X] <: Col[X]](c: Comp[CC]): BF[A, CC[A]] = ???
      |
      |  def t1(c: Col[Int]) = c.to(ColComp).head
      |  def t2(c: Col[Int]) = c.to(co(ColComp)).head
      |  def t3(c: Col[Int]) = c.to(co(ColComp): BF[Int, Col[Int]]).head
      |  def t4(c: Col[Int]) = (c.to(ColComp): Col[Int]).head
      |}
      |""".stripMargin
  )
}

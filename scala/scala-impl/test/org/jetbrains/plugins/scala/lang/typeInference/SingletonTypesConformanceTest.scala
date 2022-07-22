package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class SingletonTypesConformanceTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL11192(): Unit = checkTextHasNoErrors(
    """
      |trait HList
      |trait Second[L <: HList] {
      |  type Out
      |  def apply(value: L): Out
      |}
      |
      |object Second {
      |  type Aux[L <: HList, O] = Second[L] {type Out = O}
      |  def apply[L <: HList](implicit inst: Second[L]): Aux[L, inst.Out] = inst
      |}
    """.stripMargin
  )

  def testSCL11285(): Unit = {
    checkTextHasNoErrors(
      """trait Input {
        |  type Value
        |}
        |
        |def requestInput[Res](req: Input {type Value = Res}): Res = ???
        |
        |def test(req: Input): Unit =
        |  requestInput[req.Value](req)
      """.stripMargin)
  }

  def testSCL13607(): Unit = {
    checkTextHasNoErrors(
      """
        |trait Foo {
        |    type Bar
        |}
        |
        |def apply[A](foo: Foo { type Bar = A }): Unit = ()
        |
        |def test(f: Foo): Unit = apply[f.Bar](f)
      """.stripMargin)
  }

  def testSCL13797(): Unit = {
    checkTextHasNoErrors(
      """
        |trait Test {
        |  type X
        |  def self: Test { type X = Test.this.X } = this
        |}
      """.stripMargin)
  }

   def testSCL7017(): Unit =
    checkTextHasNoErrors(
      """
        |class SCL7017 {
        |  abstract class A
        |  case object B extends A
        |  case object C extends A
        |  case class X[T <: A](o: T, n: Int) {
        |    def +(that: X[o.type]): Int = 1
        |  }
        |  val i: Int = X(B, 1) + X(B, 2)
        |}
      """.stripMargin.trim
    )

  def testSCL18169(): Unit = checkTextHasNoErrors(
    """object DemonstrateTypeAliasError {
      |  val s: String = "7"
      |  type AliasForString = s.type
      |  val t: AliasForString = s  // required AliasForString, found String
      |}""".stripMargin
  )
}

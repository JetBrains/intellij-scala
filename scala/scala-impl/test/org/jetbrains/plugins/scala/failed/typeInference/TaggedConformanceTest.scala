package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase

/**
  * @author Roman.Shein
  * @since 02.04.2016.
  */
class TaggedConformanceTest extends TypeConformanceTestBase {

  override protected def shouldPass: Boolean = false

  def testSCL8585(): Unit = doTest(
    s"""
       |import Tag.@@
       |import Bar.LRBaz
       |
       |case class LR(left:Int, right:Int, result:Int)
       |
       |trait Baz
       |
       |object Bar {
       |  type LRBaz = LR @@ Baz
       |}
       |object Tag{
       |  type Tagged[U] = { type Tag = U }
       |  type @@[T, U] = T with Tagged[U]
       |  @inline def apply[T,U](t: T): T @@ U = t.asInstanceOf[T @@ U]
       |}
       |
       |object Foo {
       |  ${caretMarker}val baz:LRBaz = Tag(LR(1,2,3)) //good code red
       |}
       |//true
       """.stripMargin)
}

package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase

/**
  * @author Anton Yalyshev
  * @since 07.09.18.
  */
class HigherKindedTypesConformanceTest extends TypeConformanceTestBase {

  def testSCL10354(): Unit = doTest {
    s"""object X {
       |  val a = ""
       |  ${caretMarker}val b: Option[a.type] = Some(a)
       |}
       |//true""".stripMargin
  }

  def testSCL13114(): Unit = doTest {
    s"""object X {
       |  val v : List[Int] = Nil
       |}
       |
       |object Z {
       |  ${caretMarker}val x : { val v : List[T] } forSome { type T } = X
       |}
       |//true""".stripMargin
  }

  def testSubstitutedUpperBound(): Unit = doTest(
    s"""
      |trait Buffer[UUU]
      |trait RichBuffer[TT, BB[UU] <: Buffer[UU]] {
      |  type Buf = Buffer[TT]
      |}
      |
      |def richBuffer[T, B[U] <: Buffer[U]](buffer: B[T]): RichBuffer[T, B] =
      |  new RichBuffer[T, B] {
      |    // this is about checking
      |    //   B[T] <: Buffer[T]
      |    ${caretMarker}val x: Buf = buffer
      |  }
      |//true
      |""".stripMargin
  )
}

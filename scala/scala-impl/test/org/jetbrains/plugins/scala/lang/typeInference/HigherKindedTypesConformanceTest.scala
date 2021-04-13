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

  def testSCL9713(): Unit = doTest(
    """
      |import scala.language.higherKinds
      |
      |type Foo[_]
      |type Bar[_]
      |type S
      |def foo(): Foo[S] with Bar[S]
      |
      |val x: Foo[S] = foo()
      |//True
    """.stripMargin
  )

  def testSCL7319(): Unit = doTest {
    s"""trait XIndexedStateT[F[+_], -S1, +S2, +A] {
       |  def lift[M[+_]]: XIndexedStateT[({type λ[+α]=M[F[α]]})#λ, S1, S2, A] = ???
       |}
       |
       |type XStateT[F[+_], S, +A] = XIndexedStateT[F, S, S, A]
       |
       |type XId[+X] = X
       |
       |def example[S, A](s: XStateT[XId, S, A]): XStateT[Option, S, A] = {
       |  ${caretMarker}val res: XStateT[Option, S, A] = s.lift[Option]
       |  res
       |}
       |//true""".stripMargin
  }

  def testSCL9088(): Unit = doTest {
    s"""trait Bar {
       |  type FooType[T] <: Foo[T]
       |
       |  trait Foo[T] {
       |    val x: T
       |  }
       |
       |  def getFoo[T](x: T): FooType[T]
       |}
       |
       |class BarImpl extends Bar {
       |  case class FooImpl[T](x: T) extends Foo[T]
       |
       |  override type FooType[R] = FooImpl[R]
       |
       |  override def getFoo[R](x: R): FooType[R] = FooImpl[R](x)
       |}
       |
       |trait Container[B <: Bar] {
       |  val profile: B
       |
       |  ${caretMarker}val test: B#FooType[Int] = profile.getFoo[Int](5)
       |}
       |//true""".stripMargin
  }
}

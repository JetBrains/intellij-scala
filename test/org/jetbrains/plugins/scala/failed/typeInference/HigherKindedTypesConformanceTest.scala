package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase
import org.junit.experimental.categories.Category

/**
  * @author mucianm 
  * @since 28.03.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class HigherKindedTypesConformanceTest extends TypeConformanceTestBase {

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

  def testSCL4652(): Unit = doTest {
    s"""import scala.language.higherKinds
      |
      |  trait Binding[A]
      |
      |  trait ValueKey[BindingRoot] {
      |    def update(value: Any): Binding[BindingRoot]
      |  }
      |
      |  class Foo[A] {
      |    type ObjectType[B[_]] = B[A]
      |    val bar: ObjectType[Bar] = ???
      |  }
      |
      |  class Bar[A] {
      |    type ValueType = ValueKey[A]
      |    val qux: ValueType = ???
      |  }
      |
      |  object Test {
      |    def foo123(): Unit = {
      |      ${caretMarker}val g: Foo[String] => Binding[String] = _.bar.qux.update(1)
      |    }
      |  }
      |}
      |//true""".stripMargin
  }

  def testSCL10354(): Unit = doTest {
    s"""object X {
        |  val a = ""
        |  ${caretMarker}val b: Option[a.type] = Some(a)
        |}
        |//true""".stripMargin
  }
}

package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.ScalaVersion

class Scala3PolymorphicTypeTest extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def testUnderscoreMethod(): Unit = checkTextHasNoErrors(
    """
      |val f1: [T] => T => Unit =
      |  [_] => x => ???
      |val f2: [T, S] => T => S => Unit =
      |  [_, _] => x => y => ???
      |""".stripMargin
  )

  def testSCL23890(): Unit = checkTextHasNoErrors(
    """
      |trait MyTrait:
      |  def map[F[_]](f: [t] => t => F[t]): Map[MyTrait, F] = null
      |
      |trait Map[T, F[_]] extends MyTrait
      |
      |//ok, all good
      |//in the compiler same as:
      |//(??? : MyTrait).map[Option]([T >: Nothing <: Any] => (x: T) => Option.apply[T](x))
      |val value1 =
      |  (??? : MyTrait).map([T] => (x: T) => Option(x))
      |
      |//ERROR
      |//in the compiler same as:
      |//(??? : MyTrait).map[Option]([_ >: Nothing <: Any] => (x: _) => Option.apply[_](x))
      |val value2 =
      |  (??? : MyTrait).map([_] => x => Option(x))
      |
      |""".stripMargin
  )

  def testPolymorphicTypeToTupleMap(): Unit = checkTextHasNoErrors(
    """
      |val polymorphicMap01 =
      |  (1, 1).map([_] => x => Option(x))
      |
      |val polymorphicMap02 =
      |  (1, 1).map([T] => (x: T) => Option(x))
      |""".stripMargin
  )
}

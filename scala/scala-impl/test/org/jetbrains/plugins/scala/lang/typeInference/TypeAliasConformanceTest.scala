package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class TypeAliasConformanceTest extends ScalaLightCodeInsightFixtureTestCase {
  def testSCL17450(): Unit = checkTextHasNoErrors(
    """
      |object Opaque {
      |  type Opaque[+A]
      |  type OpaqueB = Opaque[Int]
      |  val b: OpaqueB = ???
      |  f(b)
      |  def f(id: Opaque[Any]): Any = ???
      |}
      |""".stripMargin
  )

  def testSCL16284(): Unit = checkTextHasNoErrors(
    """
      |trait Problem {
      |  type F[A]
      |  type G[A] <: F[A]
      |  val G: G[Int]
      |  val F: F[Int] = G
      |}""".stripMargin
  )

  // SCL-21842
  def test_literal_int_assigned_to_aliased_byte(): Unit = checkTextHasNoErrors(
    """
      |object Test {
      |  type ByteAlias = Byte
      |  val b1: ByteAlias = 1
      |  val b2: ByteAlias = (2)
      |  val b3: ByteAlias = (+3)
      |  val b4: ByteAlias = (-4)
      |  val b5: ByteAlias = -(+(-5))
      |}
      |""".stripMargin
  )

  def test_literal_int_assigned_to_aliased_char(): Unit = checkTextHasNoErrors(
    """
      |object Test {
      |  type CharAlias = Char
      |  val b1: CharAlias = 1
      |  val b2: CharAlias = (2)
      |  val b3: CharAlias = (+3)
      |  val b4: CharAlias = -(+(-4))
      |}
      |""".stripMargin
  )

  def test_literal_int_assigned_to_aliased_short(): Unit = checkTextHasNoErrors(
    """
      |object Test {
      |  type ShortAlias = Short
      |  val b1: ShortAlias = 1
      |  val b2: ShortAlias = (2)
      |  val b3: ShortAlias = (+3)
      |  val b4: ShortAlias = (-4)
      |  val b5: ShortAlias = -(+(-5))
      |}
      |""".stripMargin
  )


  def testSCL12611(): Unit = checkTextHasNoErrors(
    """
      |type Id = Short
      |final val InvalidId: Id = -1
      |//True
    """.stripMargin
  )
}

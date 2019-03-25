package org.jetbrains.plugins.scala.lang.typeInference.generated

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

class TypeInferenceSelfTypeTest extends TypeInferenceTestBase{

  def testSCL9302(): Unit = doTest {
    """
      |object SCL9302 {
      |
      |  class User
      |
      |  implicit class RichUser(user: User) {
      |    def hello(): Int = 1
      |  }
      |
      |  val user = new User
      |  user.hello()
      |
      |  trait UserTrait {
      |    this: User =>
      |
      |    /*start*/this.hello()/*end*/
      |  }
      |}
      |//Int
    """.stripMargin.trim
  }

  def testSCL9302_2(): Unit = doTest {
    """
      |trait OA
      |
      |case object OA1 extends OA
      |case object OA2 extends OA
      |
      |trait ABC[A <: OA, T[AA <: OA] <: ABC[AA, T]] {
      |  self: T[A] =>
      |
      |  def a: A
      |}
      |
      |trait XYZ[A <: OA, T[AA <: OA] <: ABC[AA, T]] {
      |  self: T[A] =>
      |
      |  /*start*/self.a/*end*/
      |}
      |
      |class Concrete[A <: OA] (override val a: A) extends ABC[A, Concrete] with XYZ[A, Concrete]
      |//AA
    """.stripMargin
  }
}
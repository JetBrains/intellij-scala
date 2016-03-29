package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * @author mucianm 
  * @since 28.03.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class ScalaZTest extends TypeInferenceTestBase {
  override protected def additionalLibraries(): Array[String] = Array("scalaz")

  def testSCL9219(): Unit = {
    doTest(
      s"""
        |import scalaz._, Scalaz._
        |val e: Either[String, Int] = Right(12)
        |val o: Option[Either[String, Int]] = Some(e)
        |//val r: Either[String, Option[Int]] = o.sequenceU
        |val r = ${START}o.sequenceU$END
        |
        |//Either[String, Option[Int]]
      """.stripMargin)
  }

  def testSCL5706(): Unit = {
    doTest(
      s"""
         |import scalaz._, Scalaz._
         |object Application {
         |  type Va[+A] = ValidationNel[String, A]
         |
         |  def v[A](field: String, validations: Va[A]*): (String, Va[List[A]]) = {
         |    (field, ${START}validations.toList.sequence$END)
         |  }
         |}
         |
         |
         |//Va[List[A]]
       """.stripMargin
    )
  }
}

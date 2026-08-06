package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

class CaseClassTypeInferenceTest extends TypeInferenceTestBase {

  override def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_12

  def testSCL11159a(): Unit = {
    doTest(
      s"""import java.util.concurrent.atomic.AtomicReference
         |
         |object UnaryOps {
         |
         |  case class Test(value: Int = 0)
         |  val atomic: AtomicReference[Test] = new AtomicReference(Test())
         |  atomic.getAndUpdate(${START}(t: Test) => t.copy(value = 2)$END)
         |}
         |//UnaryOperator[UnaryOps.Test]
      """.stripMargin)
  }

  def testSCL11159b(): Unit = {
    doTest(
      s"""import java.util.concurrent.atomic.AtomicReference
         |
         |object UnaryOps {
         |
         |  case class Test(value: Int = 0)
         |  val atomic: AtomicReference[Test] = new AtomicReference(Test())
         |  atomic.getAndUpdate(${START}_.copy(value = 1)$END)
         |}
         |//UnaryOperator[UnaryOps.Test]
      """.stripMargin)
  }
}

package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * Created by Anton Yalyshev on 17/05/16.
  */
@Category(Array(classOf[PerfCycleTests]))
class CaseClassTypeInferenceTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  def testSCL10292(): Unit = {
    doTest(
      s"""
         |case class Foo(a: Int)
         |Foo.getClass.getMethods.find(${START}x => x.getName == "apply"$END)
         |//(Nothing) => Boolean
      """.stripMargin)
  }

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

package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * Created by Anton Yalyshev on 17/05/16.
  */
@Category(Array(classOf[PerfCycleTests]))
class CaseClassTypeInferenceTest extends TypeInferenceTestBase {

  def testSCL10292(): Unit = {
    doTest(
      s"""
         |case class Foo(a: Int)
         |Foo.getClass.getMethods.find(${START}x => x.getName == "apply"$END)
         |//(Nothing) => Boolean
      """.stripMargin)
  }

}

package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class InfixCallTest  extends TypeInferenceTestBase{
  def testSCL6736(): Unit = {
    doTest(
      s"""val concatenate =  "%s%s" format (_: String, _: String )
          |${START}concatenate$END
          |//(String, String) => String""".stripMargin)
  }
}

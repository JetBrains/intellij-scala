package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * Created by mucianm on 23.03.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class Primitives extends TypeInferenceTestBase {
  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL7521() = doTest()
  
  def testSCL2045() = doTest(
    """    def returnsANumber = {
      |      if (1==1) {
      |        /*start*/0/*end*/
      |      } else {
      |        0.0
      |      }
      |    }
      |    
      |    //Double""".stripMargin)
}

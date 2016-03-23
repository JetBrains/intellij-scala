package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Alefas
  * @since 21/03/16
  */

@Category(Array(classOf[PerfCycleTests]))
class ImplicitsTest extends TypeInferenceTestBase {
  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL8242(): Unit = doTest()

  def testSCL9076(): Unit = doTest()

  def testSCL9525(): Unit = doTest()

  def testSCL9961(): Unit = doTest()
  
  def testSCL3987(): Unit = doTest()
}

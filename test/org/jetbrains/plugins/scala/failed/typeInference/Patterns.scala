package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * Created by mucianm on 22.03.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class Patterns extends TypeInferenceTestBase {
  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL9137(): Unit = doTest()

  def testSCL4500(): Unit = doTest()

  def testSCL8171(): Unit = {
    val text =
      s"""import scala.collection.immutable.NumericRange
          |
          |val seq = Seq("")
          |val x = seq match {
          |  case nr: NumericRange[_] => ${START}nr$END
          |  case _ => null
          |}
          |//NumericRange[_]""".stripMargin
    doTest(text)
  }

}

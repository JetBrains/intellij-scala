package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * Created by kate on 4/4/16.
  */

@Category(Array(classOf[PerfCycleTests]))
class ScalaFutureTest extends TypeInferenceTestBase {
  override def folderPath: String = super.folderPath + "bugs5/"

  // wrong highlighting in scala lang 2.10.
  // 2.11, 2.12 - ok
  def testSCL9677() = doTest(
    s"""
       |import scala.concurrent.Future
       |
       |
       |val s = for (i <- 1 to 100) yield Future.successful(0)  // infers IndexedSeq[Future[Int]] correctly
       |
       |//Future.sequence(s) //correct
       |Future.sequence{${START}s$END}
       |
       |//IndexedSeq[Future[Int]]
    """.stripMargin)

}

package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

import java.nio.file.Path

class PatternsTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  override def folderPath: Path = super.folderPath / "bugs5"

  def testSCL4989(): Unit = {
    doTest(
      s"""
        |val x: Product2[Int, Int] = (10, 11)
        |val (y, _) = x
        |${START}y$END
        |//Int
      """.stripMargin,
      failIfNoAnnotatorErrorsInFileIfTestIsSupposedToFail = false
    )
  }

  def testSCL6383(): Unit = {
    doTest(
      s"""
         |object Test {
         |  class R[T]
         |  case object MyR extends R[Int]
         |  def buggy[T] : PartialFunction[R[T], T] = { case MyR => ${START}3$END }
         |}
         |//T
      """.stripMargin)
  }

  def testSCL9094(): Unit = doTest()
}

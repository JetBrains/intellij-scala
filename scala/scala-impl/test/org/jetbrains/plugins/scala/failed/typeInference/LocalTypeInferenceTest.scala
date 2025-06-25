package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

import java.nio.file.Path

class LocalTypeInferenceTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  override def folderPath: Path = super.folderPath / "bugs5"

  def testSCL7970(): Unit = doTest(
    """
      |trait Set[-A]{
      |  private val self = this
      |
      |  def contains(e: A): Boolean
      |
      |  def x[B](other: Set[B]): Set[(A, B)] = new Set[(A, B)] {
      |    override def contains(e: (A, B)): Boolean = (self /*start*/contains/*end*/ e._1) && (other contains e._2)
      |  }
      |}
      |//(A) => Boolean
    """.stripMargin)
}

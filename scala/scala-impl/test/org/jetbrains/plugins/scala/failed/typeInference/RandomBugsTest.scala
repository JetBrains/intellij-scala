package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

import java.nio.file.Path

class RandomBugsTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  override def folderPath: Path = super.folderPath / "bugs5"

  def testSCL7333(): Unit = doTest()

  def testSCL8582(): Unit = doTest()
}

package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

import java.nio.file.Path

class JavaConstructorParameters extends TypeInferenceTestBase{

  override protected def shouldPass: Boolean = false

  override def folderPath: Path = super.folderPath / "bugs5"

  def testSCL9875(): Unit = doTest()

  def testSCL12071(): Unit = doTest()

  def testSCL11568(): Unit = doTest()
}

package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class InheritedTypeTest extends TypeInferenceTestBase {

  override def folderPath: Path = super.folderPath / "bugs5"

  def testSCL8411(): Unit = doTest()
}

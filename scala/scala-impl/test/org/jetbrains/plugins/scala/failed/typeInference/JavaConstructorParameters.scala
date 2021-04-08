package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
  * Created by kate on 4/1/16.
  */

class JavaConstructorParameters extends TypeInferenceTestBase{

  override protected def shouldPass: Boolean = false

  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL9875(): Unit = doTest()

  def testSCL12071(): Unit = doTest()

  def testSCL11568(): Unit = doTest()
}

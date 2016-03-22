package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
  * Created by mucianm on 22.03.16.
  */
class RandomBugs extends TypeInferenceTestBase {
  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL7521() = doTest()

}

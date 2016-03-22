package org.jetbrains.plugins.scala.failed.typeInference

import junit.framework.Assert
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
  * Created by mucianm on 22.03.16.
  */
class Patterns extends TypeInferenceTestBase {
  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL9137(): Unit = doTest()

}

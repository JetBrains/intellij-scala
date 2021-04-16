package org.jetbrains.plugins.scala.failed.resolve

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.codeInspection.internal.AnnotatorBasedErrorInspection

/**
  * Created by Roman.Shein on 02.09.2016.
  */
class HigherKindedTypesTest extends FailableResolveTest("higherKinded") {

  override protected def shouldPass: Boolean = false

  def testSCL12929(): Unit = doTest()
}

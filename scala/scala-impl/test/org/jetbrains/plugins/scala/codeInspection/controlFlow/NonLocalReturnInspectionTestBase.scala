package org.jetbrains.plugins.scala.codeInspection.controlFlow

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

abstract class NonLocalReturnInspectionTestBase extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[NonLocalReturnInspection]

  override protected val description: String = NonLocalReturnInspection.annotationDescription
}

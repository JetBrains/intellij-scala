package org.jetbrains.plugins.scala.codeInspection.shadowing


import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.codeInspection.shadow.PrivateShadowInspection

abstract class PrivateShadowInspectionTestBase extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[PrivateShadowInspection]

  override protected val description: String = PrivateShadowInspection.annotationDescription
}





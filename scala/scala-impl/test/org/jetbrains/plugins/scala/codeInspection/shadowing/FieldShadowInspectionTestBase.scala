package org.jetbrains.plugins.scala.codeInspection.shadowing


import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaQuickFixTestBase
import org.jetbrains.plugins.scala.codeInspection.shadow.FieldShadowInspection

abstract class FieldShadowInspectionTestBase extends ScalaQuickFixTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[FieldShadowInspection]

  override protected val description: String = FieldShadowInspection.annotationDescription
}





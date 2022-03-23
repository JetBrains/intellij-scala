package org.jetbrains.plugins.scala.codeInspection.shadowing


import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaQuickFixTestBase
import org.jetbrains.plugins.scala.codeInspection.shadow.FieldShadowingInspection

abstract class FieldShadowInspectionTestBase extends ScalaQuickFixTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[FieldShadowingInspection]

  override protected val description: String = FieldShadowingInspection.annotationDescription
}





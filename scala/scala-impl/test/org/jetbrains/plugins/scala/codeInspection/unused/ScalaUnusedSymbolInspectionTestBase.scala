package org.jetbrains.plugins.scala.codeInspection.unused

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaQuickFixTestBase}
import org.jetbrains.plugins.scala.codeInspection.unusedInspections.{DeleteUnusedElementFix, ScalaUnusedSymbolInspection}

abstract class ScalaUnusedSymbolInspectionTestBase extends ScalaQuickFixTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalaUnusedSymbolInspection]

  override protected val description: String =
    ScalaUnusedSymbolInspection.annotationDescription

  val hint = ScalaInspectionBundle.message("remove.unused.element")
}

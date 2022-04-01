package org.jetbrains.plugins.scala.codeInspection.unused

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.unusedInspections.{ScalaUnusedDeclarationInspection, ScalaUnusedDeclarationInspectionBase}
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaQuickFixTestBase}

abstract class ScalaUnusedDeclarationInspectionTestBase extends ScalaQuickFixTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalaUnusedDeclarationInspection]

  override protected val description: String =
    ScalaUnusedDeclarationInspectionBase.annotationDescription

  val removeUnusedElementHint = ScalaInspectionBundle.message("remove.unused.element")
  val disablePublicDeclarationReporting = ScalaInspectionBundle.message("fix.unused.declaration.report.public.declarations")
  val hintWholeDefinition = "Remove whole definition"
  val hintOnlyXBinding = "Remove only x binding"
}

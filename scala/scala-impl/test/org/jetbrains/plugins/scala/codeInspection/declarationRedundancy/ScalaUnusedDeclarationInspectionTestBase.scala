package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}

abstract class ScalaUnusedDeclarationInspectionTestBase extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalaUnusedDeclarationInspection]

  override protected val description: String =
    ScalaUnusedDeclarationInspection.annotationDescription

  val removeUnusedElementHint = ScalaInspectionBundle.message("remove.unused.element")
  val addScalaAnnotationUnusedHint = ScalaInspectionBundle.message("annotate.declaration.with.unused")
  val hintWholeDefinition = "Remove whole definition"
  val hintOnlyXBinding = "Remove only x binding"

  protected override def setUp(): Unit = {
    super.setUp()

    deleteAllGlobalScratchFiles(getProject)
  }

  protected def getInspectionTool: ScalaUnusedDeclarationInspection = {
    val inspectionProfile = InspectionProjectProfileManager.getInstance(getProject).getCurrentProfile
    val inspectionToolWrapper = inspectionProfile.getInspectionTool("ScalaUnusedSymbol", getProject)
    inspectionToolWrapper.getTool.asInstanceOf[ScalaUnusedDeclarationInspection]
  }
}

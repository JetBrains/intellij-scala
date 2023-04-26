//package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy
//
//import com.intellij.openapi.editor.Document
//import com.intellij.openapi.project.Project
//import com.intellij.profile.codeInspection.InspectionProjectProfileManager
//import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
//
//final class ScalaUnusedDeclarationPass(file: ScalaFile, doc: Option[Document])
//  extends InspectionBasedHighlightingPass(file, doc, ScalaUnusedDeclarationPass.inspection(file.getProject))
//
//object ScalaUnusedDeclarationPass {
//  def inspection(project: Project): ScalaUnusedDeclarationInspection =
//    Option(InspectionProjectProfileManager.getInstance(project))
//      .map(_.getCurrentProfile)
//      .flatMap(profile => Option(profile.getInspectionTool("ScalaUnusedSymbol", project)))
//      .map(_.getTool.asInstanceOf[ScalaUnusedDeclarationInspection])
//      .getOrElse(new ScalaUnusedDeclarationInspection)
//}

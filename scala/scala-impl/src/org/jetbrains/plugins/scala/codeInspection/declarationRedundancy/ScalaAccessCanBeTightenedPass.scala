package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile


final class ScalaAccessCanBeTightenedPass(file: ScalaFile, doc: Option[Document])
  extends InspectionBasedHighlightingPass(file, doc, ScalaAccessCanBeTightenedPass.inspection(file.getProject))

object ScalaAccessCanBeTightenedPass {
  def inspection(project: Project): ScalaAccessCanBeTightenedInspection =
    Option(InspectionProjectProfileManager.getInstance(project))
      .map(_.getCurrentProfile)
      .flatMap(profile => Option(profile.getInspectionTool("ScalaWeakerAccess", project)))
      .map(_.getTool.asInstanceOf[ScalaAccessCanBeTightenedInspection])
      .getOrElse(new ScalaAccessCanBeTightenedInspection)
}

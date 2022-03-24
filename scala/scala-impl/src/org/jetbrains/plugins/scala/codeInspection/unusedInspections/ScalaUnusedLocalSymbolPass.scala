package org.jetbrains.plugins.scala.codeInspection.unusedInspections

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
  * Created by Svyatoslav Ilinskiy on 11.07.16.
  */
final class ScalaUnusedLocalSymbolPass(file: ScalaFile, doc: Option[Document])
  extends InspectionBasedHighlightingPass(file, doc, ScalaUnusedLocalSymbolPass.inspection(file.getProject))

object ScalaUnusedLocalSymbolPass {
  def inspection(project: Project): ScalaUnusedSymbolInspection =
    Option(InspectionProjectProfileManager.getInstance(project))
      .map(_.getCurrentProfile)
      .flatMap(profile => Option(profile.getInspectionTool("ScalaUnusedSymbol", project)))
      .map(_.getTool.asInstanceOf[ScalaUnusedSymbolInspection])
      .getOrElse(new ScalaUnusedSymbolInspection)
}

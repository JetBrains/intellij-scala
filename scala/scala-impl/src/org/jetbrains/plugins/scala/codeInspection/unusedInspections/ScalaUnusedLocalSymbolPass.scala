package org.jetbrains.plugins.scala.codeInspection.unusedInspections

import com.intellij.openapi.editor.Document
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
  * Created by Svyatoslav Ilinskiy on 11.07.16.
  */
class ScalaUnusedLocalSymbolPass(file: ScalaFile, doc: Option[Document]) extends
  InspectionBasedHighlightingPass(file, doc, ScalaUnusedLocalSymbolPass.inspection)

object ScalaUnusedLocalSymbolPass {
  lazy val inspection: ScalaUnusedSymbolInspection = new ScalaUnusedSymbolInspection
}

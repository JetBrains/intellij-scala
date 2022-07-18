package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.openapi.editor.Document
import org.jetbrains.plugins.scala.codeInspection.varCouldBeValInspection.VarCouldBeValInspection
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class ScalaLocalVarCouldBeValPass(file: ScalaFile, doc: Option[Document])
  extends InspectionBasedHighlightingPass(file, doc, new VarCouldBeValInspection)

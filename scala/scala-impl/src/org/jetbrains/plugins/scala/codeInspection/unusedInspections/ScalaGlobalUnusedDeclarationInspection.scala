package org.jetbrains.plugins.scala.codeInspection.unusedInspections

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.{CommonProblemDescriptor, GlobalInspectionContext, GlobalInspectionTool, InspectionManager}
import com.intellij.codeInspection.reference.{RefEntity, RefGraphAnnotator}

import scala.annotation.unused

@unused("registered in scala-plugin-common.xml")
private class ScalaGlobalUnusedDeclarationInspection extends GlobalInspectionTool {
  override def checkElement(
    refEntity: RefEntity,
    scope: AnalysisScope,
    manager: InspectionManager,
    globalContext: GlobalInspectionContext)
  : Array[CommonProblemDescriptor] = {
    println(s"refEntity: $refEntity")
    Array.empty
  }
}

class ScalaRefGraphAnnotator extends RefGraphAnnotator {

}
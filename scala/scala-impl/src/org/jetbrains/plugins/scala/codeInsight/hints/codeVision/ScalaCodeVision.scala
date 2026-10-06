package org.jetbrains.plugins.scala.codeInsight.hints.codeVision

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

private object ScalaCodeVision {
  final val ReferencesId = "scala.references"
  final val InheritorsId = "scala.inheritors"

  /** Above this number the hint shows "N+" instead of an exact count */
  final val CountingLimit = 5

  def acceptsFile(file: PsiFile): Boolean =
    Registry.is("scala.code.vision.inlay") && (file match {
      case scalaFile: ScalaFile => !scalaFile.isWorksheetFile //SCL-21098
      case _ => false
    })
}

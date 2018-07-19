package org.jetbrains.plugins.scala.worksheet.cell

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.psi.PsiElement

/**
  * User: Dmitry.Naydanov
  */
class RunCellMarkerContributor extends RunLineMarkerContributor {
  override def getInfo(psiElement: PsiElement): RunLineMarkerContributor.Info = {
    CellManager.getInstance(psiElement.getProject).getCellFor(psiElement) match {
      case Some(descriptor) =>
        new RunLineMarkerContributor.Info(
          AllIcons.RunConfigurations.TestState.Run,
          descriptor.createRunAction.toArray,
          new java.util.function.Function[PsiElement, String] {
            override def apply(v1: PsiElement): String = "Run"
          }
        )
      case _ => null
    }
  }
}

package org.jetbrains.plugins.scala.worksheet.ammonite.runconfiguration

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.WorksheetBundle
import java.util.function.{Function => JFunction}
import org.jetbrains.plugins.scala.worksheet.ammonite.AmmoniteUtil

class AmmoniteRunMarkerContributor extends RunLineMarkerContributor {
  override def getInfo(element: PsiElement): RunLineMarkerContributor.Info = {
    val isFirstLeafElementInTheFile = element match {
      case leaf: LeafPsiElement =>
        leaf.getPrevSibling == null && PsiTreeUtil.prevLeaf(leaf) == null
      case _ =>
        false
    }
    if (!isFirstLeafElementInTheFile)
      return null

    val ammoniteFile = element.getContainingFile match {
      case file: ScalaFile if AmmoniteUtil.isAmmoniteFile(file) =>
        file
      case _ =>
        return null
    }

    val actions = Array[AnAction](new AmmoniteRunScriptAction(ammoniteFile))
    val tooltipProvider: JFunction[_ >: PsiElement, String] = (_: PsiElement) => WorksheetBundle.message("ammonite.run.script")
    new RunLineMarkerContributor.Info(
      AllIcons.RunConfigurations.TestState.Run,
      actions,
      tooltipProvider
    )
  }
}

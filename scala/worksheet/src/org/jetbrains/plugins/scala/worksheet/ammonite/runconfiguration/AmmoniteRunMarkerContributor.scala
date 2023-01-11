package org.jetbrains.plugins.scala.worksheet.ammonite.runconfiguration

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.WorksheetBundle
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

    new RunLineMarkerContributor.Info(
      AllIcons.RunConfigurations.TestState.Run,
      (_: PsiElement) => WorksheetBundle.message("ammonite.run.script"),
      new AmmoniteRunScriptAction(ammoniteFile)
    )
  }
}

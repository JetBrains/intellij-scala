package org.jetbrains.plugins.scala.worksheet.ammonite.runconfiguration

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.WorksheetBundle
import org.jetbrains.plugins.scala.worksheet.ammonite.AmmoniteUtil

class AmmoniteRunMarkerContributor extends RunLineMarkerContributor {
  override def getInfo(element: PsiElement): RunLineMarkerContributor.Info = {
    element.getParent match {
      case ammoniteFile: ScalaFile if AmmoniteUtil.isAmmoniteFile(ammoniteFile) && ammoniteFile.getFirstChild == element =>
        new RunLineMarkerContributor.Info(AllIcons.RunConfigurations.TestState.Run, (param: PsiElement) => WorksheetBundle.message("ammonite.run.script"), new AmmoniteRunScriptAction(ammoniteFile))
      case _ => null
    }
  }
}

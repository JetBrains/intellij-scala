package org.jetbrains.plugins.scala.runner

import com.intellij.execution.lineMarker.RunLineMarkerContributor.Info
import com.intellij.execution.lineMarker.{ExecutorAction, RunLineMarkerContributor}
import com.intellij.icons.AllIcons
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.runner.ScalaRunLineMarkerContributor.RunIcon
import org.jetbrains.plugins.scala.util.ScalaMainMethodUtil

import javax.swing.Icon

class ScalaRunLineMarkerContributor extends RunLineMarkerContributor {
  override def getInfo(element: PsiElement): Info = {
    element.getContainingFile match {
      case scriptLikeFile: ScalaFile
        if scriptLikeFile.isWorksheetFile || scriptLikeFile.isMultipleDeclarationsAllowed =>
        return null
      case _ =>
    }

    val isIdentifier = element.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER
    val hasMain = element.getParent match {
      case fun: ScFunctionDefinition =>
        ScalaMainMethodUtil.isMainMethod(fun)
      case c: PsiClass =>
        ScalaMainMethodUtil.hasMainMethodFromProvidersOnly(c)
      case _ =>
        false
    }
    if (isIdentifier && hasMain)
      new Info(RunIcon, ExecutorAction.getActions(0), null)
    else
      null
  }
}

object ScalaRunLineMarkerContributor {
  val RunIcon: Icon = AllIcons.RunConfigurations.TestState.Run
}

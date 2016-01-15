package org.jetbrains.plugins.scala.runner

import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.execution.lineMarker.RunLineMarkerContributor.Info
import com.intellij.execution.lineMarker.{ExecutorAction, RunLineMarkerContributor}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

/**
 * User: Dmitry.Naydanov
 * Date: 23.10.15.
 */
class ScalaRunLineMarkerContributor extends RunLineMarkerContributor {
  override def getInfo(element: PsiElement): Info = 
    if (element.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER) element.getParent match {
      case fun: ScFunctionDefinition if ScalaApplicationConfigurationProducer.findMain(fun, firstContMethodOnly = true) != null => createInfo(1)
      case obj: ScObject if ScalaApplicationConfigurationProducer.getMainClass(element, firstTemplateDefOnly = true) != null => createInfo(0)
      case _ => null
    } else null
  
  private def createInfo(order: Int) = new Info(ApplicationConfigurationType.getInstance.getIcon, null, ExecutorAction.getActions(order): _*)
}

package org.jetbrains.plugins.scala.lang.structureView

import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import com.intellij.psi._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

object ScalaElementPresentation {
  def getFilePresentableText(file: ScalaFile): String = {
    return file.getName()
  }
  def getPackagingPresentableText(packaging: ScPackaging): String = {
    return packaging.getPackageName
  }
  def getTypeDefinitionPresentableText(typeDefinition: ScTypeDefinition): String = {
    if (typeDefinition.nameNode != null)
      return typeDefinition.nameNode.getText()
    else
      return "unnamed"
  }
  def getMethodPresentableText(function: ScFunction): String = {
    val presentableText: StringBuffer = new StringBuffer("")
    presentableText.append(function.getNameNode.getText)
    if (function.getTypeParam != null)
      presentableText.append(function.getTypeParam.getText)
    if (function.getParametersClauses != null)
      presentableText.append(function.getParametersClauses.getParametersAsString)
    if (function.getReturnTypeNode != null) {
      presentableText.append(": ")
      presentableText.append(function.getReturnTypeNode.getText)
    }
    return presentableText.toString()
  }
  def getPresentableText(elem: PsiElement): String = {
    return elem.getText
  }
}
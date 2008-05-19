package org.jetbrains.plugins.scala.lang.structureView

import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

object ScalaElementPresentation {

  //TODO refactor with name getters

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

  def getPrimaryConstructorPresentableText(constructor: ScPrimaryConstructor): String = {
    val presentableText: StringBuffer = new StringBuffer("")
    if (constructor.getClassNameText != null)
      presentableText.append(constructor.getClassNameText)
    else
      presentableText.append("unnamed")
    if (constructor.typeParametersClause != null)
      presentableText.append(constructor.typeParametersClause.getText)
    if (constructor.getParametersClauses != null)
      presentableText.append(constructor.getParametersClauses.getParametersAsString)
    return presentableText.toString()
  }

  def getMethodPresentableText(function: ScFunction): String = {
    val presentableText: StringBuffer = new StringBuffer("")
    if (function.getNameNode != null)
      presentableText.append(function.getNameNode.getText)
    else
      presentableText.append("unnamed")
    if (function.typeParametersClause != null)
      presentableText.append(function.typeParametersClause.getText)
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
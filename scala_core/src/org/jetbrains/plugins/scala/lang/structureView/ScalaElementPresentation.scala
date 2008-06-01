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
    if (typeDefinition.nameId != null)
      return typeDefinition.nameId.getText()
    else
      return "unnamed"
  }

  def getPrimaryConstructorPresentableText(constructor: ScPrimaryConstructor): String = {
    val presentableText: StringBuffer = new StringBuffer("")
    if (constructor.getClassNameText != null)
      presentableText.append(constructor.getClassNameText)
    else
      presentableText.append("unnamed")
    if (constructor.parameters != null)
      presentableText.append(constructor.parameters.getParametersAsString)
    return presentableText.toString()
  }

  def getMethodPresentableText(function: ScFunction): String = {
    val presentableText: StringBuffer = new StringBuffer("")
      presentableText.append(function.getName)

    function.typeParametersClause match {
      case Some(clause) => presentableText.append(clause.getText)
      case _ => ()
    }

    if (function.paramClauses != null)
      presentableText.append(function.paramClauses.getParametersAsString)
    if (function.getReturnScTypeElement != null) {
      presentableText.append(": ")
      presentableText.append(function.getReturnScTypeElement.getText)
    }
    return presentableText.toString()
  }

  def getPresentableText(elem: PsiElement): String = {
    return elem.getText
  }
}
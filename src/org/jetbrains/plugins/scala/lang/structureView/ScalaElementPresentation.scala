package org.jetbrains.plugins.scala
package lang
package structureView

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import psi.api.ScalaFile
import com.intellij.openapi.project.IndexNotReadyException
import psi.types.{ScSubstitutor, ScType}

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

object ScalaElementPresentation {

  //TODO refactor with name getters

  def getFilePresentableText(file: ScalaFile): String = file.getName

  def getPackagingPresentableText(packaging: ScPackaging): String = packaging.getPackageName

  def getTypeDefinitionPresentableText(typeDefinition: ScTypeDefinition): String =
    if (typeDefinition.nameId != null) typeDefinition.nameId.getText else "unnamed"

  def getPrimaryConstructorPresentableText(constructor: ScPrimaryConstructor): String = {
    val presentableText: StringBuffer = new StringBuffer
    presentableText.append("this")
    if (constructor.parameters != null)
      presentableText.append(StructureViewUtil.getParametersAsString(constructor.parameterList))
    presentableText.toString
  }

  def getMethodPresentableText(function: ScFunction, short: Boolean = true,
                               subst: ScSubstitutor = ScSubstitutor.empty): String = {
    val presentableText: StringBuffer = new StringBuffer
    presentableText.append(if (!function.isConstructor) function.getName else "this")

    function.typeParametersClause match {
      case Some(clause) => presentableText.append(clause.getText)
      case _ => ()
    }

    if (function.paramClauses != null)
      presentableText.append(StructureViewUtil.getParametersAsString(function.paramClauses, short, subst))

    presentableText.append(": ")
    try {
      val typez = subst.subst(function.returnType.getOrAny)
      presentableText.append(ScType.presentableText(typez))
    }
    catch {
      case e: IndexNotReadyException => presentableText.append("NoTypeInfo")
    }

    presentableText.toString
  }

  def getTypeAliasPresentableText(typeAlias: ScTypeAlias): String =
    if (typeAlias.nameId != null) typeAlias.nameId.getText else "type unnamed"

  def getPresentableText(elem: PsiElement): String = elem.getText
}
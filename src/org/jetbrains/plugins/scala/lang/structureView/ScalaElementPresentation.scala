package org.jetbrains.plugins.scala
package lang
package structureView

import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContextOwner
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScTypeExt}

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

object ScalaElementPresentation {

  //TODO refactor with name getters

  def getFilePresentableText(file: ScalaFile): String = file.name

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

  def getMethodPresentableText(function: ScFunction, fast: Boolean = true,
                               subst: ScSubstitutor = ScSubstitutor.empty): String = {
    val presentableText: StringBuffer = new StringBuffer
    presentableText.append(if (!function.isConstructor) function.name else "this")

    function.typeParametersClause.foreach(clause => presentableText.append(clause.getText))

    if (function.paramClauses != null)
      presentableText.append(StructureViewUtil.getParametersAsString(function.paramClauses, fast, subst))

    if (fast) {
      function.returnTypeElement match {
        case Some(rt) => presentableText.append(": ").append(rt.getText)
        case _ => //do nothing
      }
    } else {
      presentableText.append(": ")
      try {
        val typez = subst.subst(function.returnType.getOrAny)
        presentableText.append(typez.presentableText)
      }
      catch {
        case e: IndexNotReadyException => presentableText.append("NoTypeInfo")
      }
    }

    presentableText.toString
  }

  def getTypeAliasPresentableText(typeAlias: ScTypeAlias): String =
    if (typeAlias.nameId != null) typeAlias.nameId.getText else "type unnamed"

  def getPresentableText(elem: PsiElement): String = elem.getText

  def getValOrVarPresentableText(elem: ScNamedElement): String = {
    val typeText = elem match {
      case typed: TypingContextOwner => ": " + typed.getType().getOrAny.presentableText
      case _ => ""
    }
    val keyword = ScalaPsiUtil.nameContext(elem) match {
      case _: ScVariable => ScalaKeyword.VAR
      case _: ScValue => ScalaKeyword.VAL
      case param: ScClassParameter if param.isVar => ScalaKeyword.VAR
      case param: ScClassParameter if param.isVal => ScalaKeyword.VAL
      case _ => ""
    }
    s"$keyword ${elem.name}$typeText"
  }
}
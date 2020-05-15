package org.jetbrains.plugins.scala.lang
package psi

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAccessModifier
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectContextOwner}

object PresentationUtil {

  def presentationString(owner: ProjectContextOwner): String =
    presentationString(owner.asInstanceOf[Any])(owner.projectContext, TypePresentationContext.emptyContext)

  def presentationString(obj: Any)(implicit project: ProjectContext, tpc: TypePresentationContext): String =
    presentationString(obj, ScSubstitutor.empty)

  def presentationString(obj: Any, substitutor: ScSubstitutor)
                        (implicit project: ProjectContext, tpc: TypePresentationContext): String = {

    val res: String = obj match {
      case null => ""
      case psiElement: PsiElement =>
        presentationStringForPsiElement(psiElement, substitutor)
      case scType: ScType =>
        presentationStringForScalaType(scType, substitutor)
      case psiType: PsiType =>
        presentationStringForJavaType(psiType, substitutor)
      case param: Parameter =>
        val builder = new StringBuilder
        builder.append(param.name)
        builder.append(": " + presentationStringForScalaType(param.paramType, substitutor))
        if (param.isRepeated) builder.append("*")
        if (param.isDefault) builder.append(" = _")
        builder.toString()
      case _ => obj.toString
    }
    res.replace(SyntheticClasses.TypeParameter, "T")
  }

  private def presentationStringForScalaType(scType: ScType, substitutor: ScSubstitutor): String =
    // empty context is used just because it was so before refactoring
    substitutor(scType).presentableText(TypePresentationContext.emptyContext)

  private def presentationStringForJavaType(psiType: PsiType, substitutor: ScSubstitutor)
                                           (implicit project: ProjectContext): String =
    psiType match {
      case tp: PsiEllipsisType =>
        presentationStringForJavaType(tp.getComponentType, substitutor) + "*"
      case _         =>
        presentationStringForScalaType(psiType.toScType(), substitutor)
    }

  private def presentationStringForPsiElement(element: PsiElement, substitutor: ScSubstitutor)
                                             (implicit project: ProjectContext, tpc: TypePresentationContext): String =
    element match {
      case clauses: ScParameters =>
        clauses.clauses.map(presentationStringForPsiElement(_, substitutor)).mkString("")
      case clause: ScParameterClause =>
        val buffer = new StringBuilder("")
        buffer.append("(")
        if (clause.isImplicit) buffer.append("implicit ")
        buffer.append(clause.parameters.map(presentationStringForPsiElement(_, substitutor)).mkString(", "))
        buffer.append(")")
        buffer.toString()
      case param: ScParameter =>
        ScalaPsiPresentationUtils.renderParameter(param)(presentationStringForScalaType(_, substitutor))
      case tp: ScTypeParamClause =>
        tp.typeParameters.map(t => presentationStringForPsiElement(t, substitutor)).mkString("[", ", ", "]")
      case param: ScTypeParam =>
        var paramText = param.name
        if (param.isContravariant) paramText = "-" + paramText
        else if (param.isCovariant) paramText = "+" + paramText
        val stdTypes = param.projectContext.stdTypes
        param.lowerBound.foreach {
          case stdTypes.Nothing =>
          case tp: ScType => paramText = paramText + " >: " + presentationStringForScalaType(tp, substitutor)
        }
        param.upperBound.foreach {
          case stdTypes.Any =>
          case tp: ScType => paramText = paramText + " <: " + presentationStringForScalaType(tp, substitutor)
        }
        param.viewBound.foreach {
          (tp: ScType) => paramText = paramText + " <% " + presentationStringForScalaType(tp, substitutor)
        }
        param.contextBound.foreach {
          (tp: ScType) => paramText = paramText + " : " + presentationStringForScalaType(ScTypeUtil.stripTypeArgs(substitutor(tp)), substitutor)
        }
        paramText
      case param: PsiTypeParameter =>
        val paramText = param.name
        //todo: possibly add supers and extends?
        paramText
      case params: PsiParameterList =>
        params.getParameters.map(presentationStringForPsiElement(_, substitutor)).mkString("(", ", ", ")")
      case param: PsiParameter =>
        val buffer: StringBuilder = new StringBuilder("")
        val list = param.getModifierList
        if (list == null) return ""
        val lastSize = buffer.length
        for (a <- list.getAnnotations) {
          if (lastSize != buffer.length) buffer.append(" ")
          val element = a.getNameReferenceElement
          if (element != null) buffer.append("@").append(element.getText)
        }
        if (lastSize != buffer.length) buffer.append(" ")
        val name = param.name
        if (name != null) {
          buffer.append(name)
        }
        buffer.append(": ")
        buffer.append(presentationStringForJavaType(param.getType, substitutor)) //todo: create param type, java.lang.Object => Any
        buffer.toString()
      case fun: ScFunction =>
        val buffer: StringBuilder = new StringBuilder("")
        fun.getParent match {
          case _: ScTemplateBody if fun.containingClass != null =>
            val qual = fun.containingClass.qualifiedName
            if (qual != null) {
              buffer.append(qual).append(".")
            }
          case _ =>
        }
        buffer.append(fun.name)
        fun.typeParametersClause match {
          case Some(tpc) =>
            // empty substitutor and context are used just because it was so before refactoring
            buffer.append(presentationStringForPsiElement(tpc, ScSubstitutor.empty)(project, TypePresentationContext.emptyContext))
          case _ =>
        }
        buffer.append(presentationStringForPsiElement(fun.paramClauses, substitutor)).append(": ")
        buffer.append(presentationStringForScalaType(fun.returnType.getOrAny, substitutor))
        buffer.toString()
      case _ =>
        element.getText
    }

  def accessModifierText(modifier: ScAccessModifier): String =
    ScalaPsiPresentationUtils.accessModifierText(modifier, fast = true)
}

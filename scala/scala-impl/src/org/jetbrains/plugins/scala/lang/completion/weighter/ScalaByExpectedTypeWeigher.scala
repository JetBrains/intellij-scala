package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.lookup.{LookupElement, LookupElementWeigher, WeighingContext}
import com.intellij.psi.{PsiElement, PsiField, PsiMethod, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiElementExt, PsiTypeExt}
import org.jetbrains.plugins.scala.lang.completion.ScalaSmartCompletionContributor
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.{Nothing, ParameterizedType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaType}
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * Created by Kate Ustyuzhanina on 11/24/16.
  */
class ScalaByExpectedTypeWeigher(position: PsiElement, isAfterNew: Boolean) extends LookupElementWeigher("scalaExpectedType") {

  private implicit def project: ProjectContext = position.projectContext

  private lazy val expectedTypes: Seq[ScType] = {
    val maybeDefinition = ScalaSmartCompletionContributor.extractReference[PsiElement](position)
      .map(_.reference).orElse {
      if (isAfterNew) position.findContextOfType(classOf[ScNewTemplateDefinition]) else None
    }

    maybeDefinition match {
      case Some(expression) => expression.expectedTypes()
      case _ => Seq.empty
    }
  }

  override def weigh(element: LookupElement, context: WeighingContext): Integer =
    ScalaLookupItem.original(element) match {
      case s: ScalaLookupItem if expectedTypes.nonEmpty && computeType(s).exists(expectedType) => 0
      case _ => 1
    }

  private def expectedType(scType: ScType): Boolean = (scType != null) &&
    (!scType.equiv(Nothing)) &&
    expectedTypes.exists {
      case tp if scType.conforms(tp) => true
      case ParameterizedType(tpe, Seq(arg)) =>
        tpe.extractClass.map(_.qualifiedName).exists {
          case "scala.Option" | "scala.Some" => true
          case _ => false
        } && scType.conforms(arg)
      case _ => false
    }

  private def computeType(item: ScalaLookupItem): Option[ScType] = {
    def helper(scType: ScType, substitutor: ScSubstitutor = ScSubstitutor.empty) = {
      val substituted = substitutor.subst(scType)
      item.substitutor.subst(substituted)
    }

    import ScalaPsiUtil.inferMethodTypesArgs
    item.element match {
      case element if !isAccessible(element, position) || item.isNamedParameterOrAssignment => None
      case fun: ScSyntheticFunction => Some(helper(fun.retType))
      case fun: ScFunction =>
        if (fun.containingClass != null && fun.containingClass.qualifiedName == "scala.Predef") {
          fun.name match {
            case "implicitly" | "identity" | "locally" => return None
            case _ =>
          }
        }
        fun.returnType.toOption
          .orElse(fun.`type`().toOption)
          .map(helper(_, inferMethodTypesArgs(fun, item.substitutor)))
      case method: PsiMethod =>
        val substitutor = inferMethodTypesArgs(method, item.substitutor)
        Some(helper(method.getReturnType.toScType(), substitutor))
      case typed: ScTypedDefinition =>
        typed.`type`().map(helper(_)).toOption
      case f: PsiField =>
        Some(helper(f.getType.toScType()))
      case el => Some(ScalaType.designator(el))
    }
  }

  private def isAccessible(element: PsiNamedElement, place: PsiElement): Boolean =
    ScalaPsiUtil.nameContext(element) match {
      case member: ScMember => ResolveUtils.isAccessible(member, place, forCompletion = true)
      case _ => true
    }
}



package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.lookup.{LookupElement, LookupElementWeigher, WeighingContext}
import com.intellij.psi.{PsiElement, PsiField, PsiMethod, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiNamedElementExt, PsiTypeExt}
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.{Nothing, ParameterizedType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaType}
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils

final class ScalaByExpectedTypeWeigher(maybeDefinition: Option[ScExpression])
                                      (implicit position: PsiElement) extends LookupElementWeigher("scalaExpectedType") {

  import ScalaByExpectedTypeWeigher._

  private lazy val expectedTypes = maybeDefinition.fold(Seq.empty[ScType]) {
    _.expectedTypes()
  }

  override def weigh(lookupElement: LookupElement, context: WeighingContext): Integer =
    if (expectedTypes.nonEmpty) {
      val elementAndSubstitutor = lookupElement match {
        case ScalaLookupItem(item, target) =>
          if (item.isNamedParameterOrAssignment) (null, null)
          else (target, item.substitutor)
        case _ => (lookupElement.getPsiElement, ScSubstitutor.empty)
      }

      elementAndSubstitutor match {
        case (element: PsiNamedElement, substitutor) if isAccessible(element) &&
          computeType(element, substitutor).exists(expectedType) => 0
        case _ => 1
      }
    } else {
      1
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
}

object ScalaByExpectedTypeWeigher {

  import ScalaPsiUtil.undefineMethodTypeParams

  private[completion] def computeType(element: PsiNamedElement, itemSubstitutor: ScSubstitutor)
                                     (implicit place: PsiElement): Option[ScType] = {
    def substitution(scType: ScType,
                     substitutor: ScSubstitutor = ScSubstitutor.empty) = {
      val substituted = substitutor(scType)
      itemSubstitutor(substituted)
    }

    element match {
      case fun: ScSyntheticFunction => Some(substitution(fun.retType))
      case fun: ScFunction =>
        if (fun.containingClass != null && fun.containingClass.qualifiedName == "scala.Predef") {
          fun.name match {
            case "implicitly" | "identity" | "locally" => return None
            case _ =>
          }
        }
        fun.returnType.toOption
          .orElse(fun.`type`().toOption)
          .map {
            substitution(_, undefineMethodTypeParams(fun))
          }
      case method: PsiMethod =>
        val substitutor = undefineMethodTypeParams(method)
        Some(substitution(method.getReturnType.toScType(), substitutor))
      case typed: ScTypedDefinition =>
        typed.`type`().toOption
          .map {
            substitution(_)
          }
      case f: PsiField => Some(substitution(f.getType.toScType()))
      case _ => Some(ScalaType.designator(element))
    }
  }

  private def isAccessible(element: PsiNamedElement)
                          (implicit place: PsiElement): Boolean = element.nameContext match {
    case member: ScMember => ResolveUtils.isAccessible(member, place, forCompletion = true)
    case _ => true
  }
}



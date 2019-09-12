package org.jetbrains.plugins.scala
package lang
package completion
package weighter

import com.intellij.codeInsight.lookup.{LookupElement, LookupElementWeigher, WeighingContext}
import com.intellij.psi.{PsiElement, PsiField, PsiMethod, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiTypeExt}
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

/**
  * Created by Kate Ustyuzhanina on 11/24/16.
  */
final class ScalaByExpectedTypeWeigher(maybeDefinition: Option[ScExpression])
                                      (implicit position: PsiElement) extends LookupElementWeigher("scalaExpectedType") {

  import ScalaByExpectedTypeWeigher._

  private lazy val expectedTypes = maybeDefinition.fold(Seq.empty[ScType]) {
    _.expectedTypes()
  }

  override def weigh(element: LookupElement, context: WeighingContext): Integer = element match {
    case ScalaLookupItem(item, target) if expectedTypes.nonEmpty &&
      isAccessible(target) &&
      !item.isNamedParameterOrAssignment =>
      if (computeType(target, item.substitutor).exists(expectedType)) 0
      else 1
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
                          (implicit place: PsiElement): Boolean = ScalaPsiUtil.nameContext(element) match {
    case member: ScMember => ResolveUtils.isAccessible(member, place, forCompletion = true)
    case _ => true
  }
}



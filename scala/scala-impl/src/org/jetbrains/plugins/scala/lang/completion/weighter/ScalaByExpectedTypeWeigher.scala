package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.lookup.{LookupElement, LookupElementWeigher, WeighingContext}
import com.intellij.psi.{PsiElement, PsiField, PsiMethod}
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiElementExt, PsiTypeExt}
import org.jetbrains.plugins.scala.lang.completion.ScalaSmartCompletionContributor
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.{Nothing, ParameterizedType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScType, ScalaType}

/**
  * Created by Kate Ustyuzhanina on 11/24/16.
  */
class ScalaByExpectedTypeWeigher(expectedTypes: Seq[ScType], position: PsiElement) extends LookupElementWeigher("scalaExpectedType") {

  private implicit def project = position.projectContext

  override def weigh(element: LookupElement, context: WeighingContext): Comparable[_] = {
    import KindWeights._
    ScalaLookupItem.original(element) match {
      case s: ScalaLookupItem if expectedTypes.nonEmpty =>
        val (_, uType) = computeType(s)

        Option(uType) match {
          case Some(tp) if expectedType(tp, s) => expected
          case _ => normal
        }
      case _ => normal
    }
  }

  object KindWeights extends Enumeration {
    val expected, normal = Value
  }

  def expectedType(scType: ScType, el: ScalaLookupItem): Boolean = {
    if (scType == null) {
      return false
    }

    if (!scType.equiv(Nothing) && expectedTypes.exists(scType conforms _)) true
    else {
      def checkParametrizedType(tpe: ScType, arg: ScType): Boolean = {
        tpe.extractClass match {
          case Some(clazz) if (clazz.qualifiedName == "scala.Option" || clazz.qualifiedName == "scala.Some") && (!scType.equiv(Nothing) && scType.conforms(arg)) =>
            true
          case _ => false
        }
      }

      expectedTypes.exists {
        case ParameterizedType(tpe, Seq(arg)) => checkParametrizedType(tpe, arg)
        case _ => false
      }
    }
  }

  def computeType(scalaLookupItem: ScalaLookupItem): (ScType, ScType) = {
    def helper(_tp: ScType, _subst: ScSubstitutor = ScSubstitutor.empty) = {
      val tp = _subst.subst(_tp)
      scalaLookupItem.substitutor.subst(tp)
    }

    scalaLookupItem match {
      case _ if !ScalaSmartCompletionContributor.isAccessible(scalaLookupItem, position) || scalaLookupItem.isNamedParameterOrAssignment =>
        (null, null)
      case _ =>
        scalaLookupItem.element match {
          case fun: ScSyntheticFunction =>
            val tp = fun.retType
            (tp, helper(tp))
          case fun: ScFunction =>
            if (fun.containingClass != null && fun.containingClass.qualifiedName == "scala.Predef") {
              fun.name match {
                case "implicitly" | "identity" | "locally" => return (null, null)
                case _ =>
              }
            }
            val subst = ScalaPsiUtil.inferMethodTypesArgs(fun, scalaLookupItem.substitutor)

            fun.returnType match {
              case Success(tp, _) => (tp, helper(tp, subst))
              case _ =>
                fun.getType(TypingContext.empty) match {
                  case Success(tp, _) => (tp, helper(tp, subst))
                  case _ => (null, null)
                }
            }
          case method: PsiMethod =>
            val subst = ScalaPsiUtil.inferMethodTypesArgs(method, scalaLookupItem.substitutor)

            val tp = method.getReturnType.toScType()
            (tp, helper(tp, subst))
          case typed: ScTypedDefinition =>
            typed.getType(TypingContext.empty) match {
              case Success(tp, _) => (tp, helper(tp))
              case _ => (null, null)
            }
          case f: PsiField =>
            val tp = f.getType.toScType()
            (tp, helper(tp))
          case el =>
            val designator = ScalaType.designator(el)
            (designator, designator)
        }
    }
  }
}



package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
  * Created by kate
  * on 1/18/16
  */
class ScalaKindCompletionWeigher extends CompletionWeigher {
  override def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    val position = ScalaCompletionUtil.positionFromParameters(location.getCompletionParameters)
    def isTypeDefiniton = Option(PsiTreeUtil.getParentOfType(position, classOf[ScTypeElement])).isDefined

    import KindWeights._

    def inCurrentClassDef(inMember: PsiMember): Boolean = {
      val cclass = inMember.getContainingClass
      (cclass != null) && (position != null && PsiTreeUtil.isContextAncestor(cclass, position, false))
    }

    def handleMember(inMember: PsiMember, position: PsiElement): KindWeights.Value = {
      val cclass = inMember.getContainingClass
      val noClass = cclass == null

      val inCurrentClass = inCurrentClassDef(inMember)
      if (inCurrentClass) {
        inMember match {
          case f: ScValue => currentClassField
          case f: ScVariable => currentClassField
          case f: PsiField => currentClassField
          case m: PsiMethod => currentClassMethod
          case _ => currentClassMember
        }
      } else {
        inMember match {
          case f: ScFunction if noClass => localFunc
          case f: ScValue => field
          case f: ScVariable => field
          case f: PsiField => field
          case m: PsiMethod => method
          case _ => member
        }
      }
    }

    def weight =
      ScalaLookupItem.original(element) match {
        case s: ScalaLookupItem if s.isLocalVariable => KindWeights.local
        case s: ScalaLookupItem =>
          s.element match {
            case p: ScClassParameter if inCurrentClassDef(p) => KindWeights.currentClassField
            case p: ScClassParameter => KindWeights.field
            case patt: ScTypedDefinition =>
              patt.nameContext match {
                case m: PsiMember => handleMember(m, position)
                case _ => null
              }
            case m: PsiMember => handleMember(m, position)
            case _ => null
          }
        case _ => null
      }

    def typedWeight =
      ScalaLookupItem.original(element) match {
        case s: ScalaLookupItem =>
          s.element match {
            case ta: ScTypeAlias if ta.isLocal => localType
            case ta: ScTypeAlias if inCurrentClassDef(ta) => typeDefinition
            case te: ScTypeDefinition if !te.isObject && (te.isLocal || inFunction(te)) => localType
            case te: ScTypeDefinition if inCurrentClassDef(te) && !te.isObject => typeDefinition
            case _ => weight
          }
        case _ => null
      }

    if (isTypeDefiniton) typedWeight
    else weight
  }

  def inFunction(psiElement: PsiElement): Boolean =
    PsiTreeUtil.getParentOfType(psiElement, classOf[ScBlockExpr]) != null

  object KindWeights extends Enumeration {
    val member, method, field,
    currentClassMember, currentClassMethod, currentClassField, localFunc, local,
    typeDefinition, currentClassType, localType = Value
  }

}
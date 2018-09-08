package org.jetbrains.plugins.scala.lang.completion
package weighter

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition

/**
  * Created by kate
  * lift fields before methods. threat class params as field.
  * on 1/18/16
  */
class ScalaKindCompletionWeigher extends CompletionWeigher {
  override def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    import KindWeights._

    def handleMember(inMember: PsiMember): Value = inMember match {
      case _ if inMember.getContainingClass == null => normal
      case _: ScValue |
           _: ScVariable |
           _: PsiField => field
      case _: PsiMethod => method
      case _ => member
    }

    positionFromParameters(location.getCompletionParameters) match {
      case position if ScalaCompletionUtil.isTypeDefiniton(position) ||
        ScalaAfterNewCompletionContributor.isAfterNew(position, location) => null
      case _ =>
        ScalaLookupItem.original(element) match {
          case ScalaLookupItem(_: ScClassParameter) => field
          case ScalaLookupItem(p: ScTypedDefinition) =>
            p.nameContext match {
              case m: PsiMember => handleMember(m)
              case _ => null
            }
          case ScalaLookupItem(m: PsiMember) => handleMember(m)
          case _ => null
        }
    }
  }

  object KindWeights extends Enumeration {
    val normal, member, method, field = Value
  }

}

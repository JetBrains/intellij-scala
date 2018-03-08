package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.codeInsight.hints.{Option => HintOption, _}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

private[hints] case object MemberHintType extends HintType {

  private[hints] val functionReturnType = HintOption(defaultValue = true, "function", "return", "type")
  private[hints] val propertyType = HintOption(defaultValue = true, "property", "type")
  private[hints] val localVariableType = HintOption(defaultValue = false, "local", "variable", "type")

  override val options: Seq[HintOption] = Seq(
    functionReturnType,
    propertyType,
    localVariableType
  )

  override def apply(element: PsiElement): Iterable[InlayInfo] = element match {
    case TypelessMember(member, anchor, option) if option.get =>
      val maybeType = member match {
        case function: ScFunction => function.returnType.toOption
        case definition: ScValueOrVariable => definition.`type`().toOption
        case _ => None
      }

      maybeType.map(InlayInfo(_, anchor))
    case _ => None
  }

  object hintOption {

    import HintInfo.OptionInfo

    def unapply(element: PsiElement): Option[OptionInfo] = element match {
      case TypelessMember(_, _, option) => Some(new OptionInfo(option))
      case _ => None
    }
  }

  private object TypelessMember {

    def unapply(member: ScMember): Option[(ScMember, PsiElement, HintOption)] = {
      val maybePair = member match {
        case function: ScFunctionDefinition => Some(function, function.parameterList)
        case value: ScPatternDefinition => Some(value, value.pList)
        case variable: ScVariableDefinition => Some(variable, variable.pList)
        case _ => None
      }

      maybePair.collect {
        case (f: ScFunction, anchor) if !f.hasExplicitType =>
          (f, anchor, functionReturnType)
        case (v: ScValueOrVariable, anchor) if !v.hasExplicitType =>
          (v, anchor, if (member.isLocal) localVariableType else propertyType)
      }
    }
  }

}
package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

trait ScTypedPatternLike extends ScPattern {
  def typePattern: Option[ScTypePattern]
}

object ScTypedPatternLike {
  def unapply(tp: ScTypedPatternLike): Option[ScTypePattern] = tp.typePattern

  object withNameId {
    def unapply(tp: ScTypedPatternLike): Option[(ScTypePattern, PsiElement)] = {
      val typePattern = tp.typePattern
      val nameId = getNameId(tp)

      typePattern.zip(nameId)
    }

    private def getNameId(pattern: ScTypedPatternLike): Option[PsiElement] = pattern match {
      case tp: ScTypedPattern =>
        tp.nameId.toOption
      case tp: Sc3TypedPattern =>
        tp.pattern match {
          case bindingPattern: ScBindingPattern => bindingPattern.nameId.toOption
          case wildcardPattern: ScWildcardPattern => wildcardPattern.findFirstChildByType(ScalaTokenTypes.tUNDER)
          // TODO: support more pattern types
          case _ => None
        }
      case _ => None
    }
  }
}

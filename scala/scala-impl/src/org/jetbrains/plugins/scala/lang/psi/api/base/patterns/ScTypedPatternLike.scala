package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement.NameId

trait ScTypedPatternLike extends ScPattern {
  def typePattern: Option[ScTypePattern]
}

object ScTypedPatternLike {
  def unapply(tp: ScTypedPatternLike): Option[ScTypePattern] = tp.typePattern

  object withNameId {
    def unapply(tp: ScTypedPatternLike): Option[(ScTypePattern, NameId)] = {
      val typePattern = tp.typePattern
      val nameId = getNameId(tp)

      typePattern.zip(nameId)
    }

    private def getNameId(pattern: ScTypedPatternLike): Option[NameId] = pattern match {
      case tp: ScTypedPattern =>
        Some(tp.nameId)
      case tp: Sc3TypedPattern =>
        tp.pattern match {
          case bindingPattern: ScBindingPattern =>
            Some(bindingPattern.nameId)
          case wildcardPattern: ScWildcardPattern =>
            Some(new NameId.Placeholder(wildcardPattern.underscoreToken))
          // TODO: support more pattern types
          case _ =>
            None
        }
      case _ =>
        None
    }
  }
}

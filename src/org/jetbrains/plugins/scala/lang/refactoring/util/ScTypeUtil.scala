package org.jetbrains.plugins.scala
package lang
package refactoring
package util

import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScDesignatorType, ScParameterizedType, ScProjectionType, ScType}


/**
 * User: Alexander Podkhalyuzin
 * Date: 30.03.2009
 */

object ScTypeUtil {

  def stripTypeArgs(tp: ScType): ScType = tp match {
    case ScParameterizedType(designator, _) => designator
    case t => t
  }

  case class AliasType(ta: ScTypeAlias, lower: TypeResult[ScType], upper: TypeResult[ScType])
  
  def removeTypeDesignator(tp: ScType): Option[ScType] = {
    tp match {
      case ScDesignatorType(v: ScBindingPattern) => v.getType(TypingContext.empty).toOption.flatMap(removeTypeDesignator)
      case ScDesignatorType(v: ScFieldId) => v.getType(TypingContext.empty).toOption.flatMap(removeTypeDesignator)
      case ScDesignatorType(p: ScParameter) => p.getType(TypingContext.empty).toOption.flatMap(removeTypeDesignator)
      case p: ScProjectionType =>
        p.actualElement match {
          case v: ScBindingPattern => v.getType(TypingContext.empty).map(p.actualSubst.subst).toOption.flatMap(removeTypeDesignator)
          case v: ScFieldId => v.getType(TypingContext.empty).map(p.actualSubst.subst).toOption.flatMap(removeTypeDesignator)
          case v: ScParameter => v.getType(TypingContext.empty).map(p.actualSubst.subst).toOption.flatMap(removeTypeDesignator)
          case _ => Some(tp)
        }
      case _ => Some(tp)
    }
  }
}
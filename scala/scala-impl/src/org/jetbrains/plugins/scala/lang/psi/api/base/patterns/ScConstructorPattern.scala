package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
* @author Alexander Podkhalyuzin
* Patterns, introduced by case classes or extractors
*/
trait ScConstructorPattern extends ScPattern {
  def args: ScPatternArgumentList = findChildByClassScala(classOf[ScPatternArgumentList])
  def ref: ScStableCodeReferenceElement = findChildByClassScala(classOf[ScStableCodeReferenceElement])
}

object ScConstructorPattern {

  def unapply(pattern: ScConstructorPattern): Option[(ScStableCodeReferenceElement, ScPatternArgumentList)] =
    Some((pattern.ref, pattern.args))

  def isIrrefutable(t: Option[ScType], ref: ScStableCodeReferenceElement, subpatterns: Seq[ScPattern]): Boolean = {
    {
      for {
        ty <- t
        resolveResult <- ref.bind()
        unapply@(_x: ScFunction) <- Option(resolveResult.getElement)
        if unapply.isUnapplyMethod
        caseClass <- Option(unapply.syntheticCaseClass)
        (clazz: ScClass, substitutor) <- ty.extractClassType
        if clazz.isCase && clazz == caseClass
        constr <- clazz.constructor
        params = constr.parameterList.clauses.headOption.map(_.parameters).getOrElse(Seq.empty)
      } yield (params, substitutor)
    } exists {
      case (params, substitutor) =>
        subpatterns.corresponds(params) {
          (pattern, param) =>
            val paramType = param.`type`().getOrElse {
              return false
            }
            if (param.isRepeatedParameter) extractsRepeatedParameterIrrefutably(pattern)
            else pattern.isIrrefutableFor(Some(substitutor(paramType)))
        }
    }
  }

  private def extractsRepeatedParameterIrrefutably(pattern: ScPattern): Boolean = {
    pattern match {
      case _: ScSeqWildcard => true
      case p: ScNamingPattern => extractsRepeatedParameterIrrefutably(p.named)
      case p: ScParenthesisedPattern => p.innerElement.exists(extractsRepeatedParameterIrrefutably)
      case _ => false
    }
  }
}
package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScMatchTypeCases, ScMatchTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScMatchType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}

class ScMatchTypeElementImpl(node: ASTNode)
  extends ScalaPsiElementImpl(node)
    with ScMatchTypeElement {

  override def scrutineeTypeElement: ScTypeElement = findChild[ScTypeElement].get

  override def cases: Option[ScMatchTypeCases] = findChild[ScMatchTypeCases]

  override protected def innerType: TypeResult = scrutineeTypeElement.`type`() match {
    case Right(scrutineeType) =>
      val caseTypes = cases match {
        case Some(casesElement) => casesElement.cases.map { cs =>
          (cs.pattern, cs.result) match {
            case (Some(patternElement), Some(resultElement)) => (patternElement.`type`(), resultElement.`type`()) match {
              case (Right(patternType), Right(resultType)) => (patternType, resultType)
              case _ => return Failure("")
            }
            case _ => return Failure("")
          }
        }
        case _ => return Failure("")
      }
      val upperBound = getContext.toOption.flatMap {
        case alias: ScTypeAliasDefinition => alias.upperBound.toOption
        case _ => None
      }
      Right(ScMatchType(scrutineeType, caseTypes, upperBound))
    case _ => Failure("")
  }
}

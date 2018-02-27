package org.jetbrains.plugins.scala
package codeInsight
package hints
package hintTypes

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScValueOrVariable, ScVariableDefinition}

private[hints] sealed abstract class DefinitionHintType(defaultValue: Boolean, idSegments: String*)
  extends HintType(defaultValue, idSegments :+ "type": _*) {

  import DefinitionHintType._

  protected val isLocal: Boolean

  override protected val delegate: HintFunction = {
    case TypelessDefinition(definition, patternList, `isLocal`) =>
      definition.`type`().toSeq
        .map(InlayInfo(_, patternList))
  }
}

private[hints] case object PropertyHintType extends DefinitionHintType(
  defaultValue = true,
  idSegments = "property"
) {
  override protected val isLocal: Boolean = false
}

private[hints] case object LocalVariableHintType extends DefinitionHintType(
  defaultValue = false,
  idSegments = "local", "variable"
) {
  override protected val isLocal: Boolean = true
}

private[hintTypes] object DefinitionHintType {

  private object TypelessDefinition {

    def unapply(element: PsiElement): Option[(ScValueOrVariable, ScPatternList, Boolean)] = element match {
      case definition: ScValueOrVariable if !definition.hasExplicitType =>
        val maybePatternList = definition match {
          case value: ScPatternDefinition => Some(value.pList)
          case variable: ScVariableDefinition => Some(variable.pList)
          case _ => None
        }

        maybePatternList.map((definition, _, definition.isLocal))
      case _ => None
    }
  }

}
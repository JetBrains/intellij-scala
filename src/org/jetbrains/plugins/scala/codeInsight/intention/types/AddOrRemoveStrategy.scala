package org.jetbrains.plugins.scala
package codeInsight
package intention
package types

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypedPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._

/**
  * Pavel.Fatin, 28.04.2010
  */
class AddOrRemoveStrategy(editor: Option[Editor] = None) extends AddOnlyStrategy(editor) {

  import AddOrRemoveStrategy._

  override def functionWithType(function: ScFunctionDefinition,
                                typeElement: ScTypeElement): Unit =
    removeTypeAnnotation(typeElement)

  override def valueWithType(value: ScPatternDefinition,
                             typeElement: ScTypeElement): Unit =
    removeTypeAnnotation(typeElement)

  override def variableWithType(variable: ScVariableDefinition,
                                typeElement: ScTypeElement): Unit =
    removeTypeAnnotation(typeElement)

  override def patternWithType(pattern: ScTypedPattern): Unit = {
    import pattern.projectContext

    val newPattern = createPatternFromText(pattern.name)
    pattern.replace(newPattern)
  }

  override def parameterWithType(parameter: ScParameter): Unit = {
    import parameter.projectContext

    val newParameter = createParameterFromText(parameter.name)

    val pair: Option[(PsiElement, PsiElement)] = Option(getParentOfType(parameter, classOf[ScFunctionExpr], false))
      .filter(_.parameters.size == 1)
      .flatMap(_.params.clauses.headOption)
      .filter { clause =>
        val text = clause.getText
        text.startsWith("(") && text.endsWith(")")
      }.map {
      (_, createClauseForFunctionExprFromText(newParameter.getText))
    }

    val (element, replacement) = pair.getOrElse {
      (parameter, newParameter)
    }

    element.replace(replacement)
  }
}

object AddOrRemoveStrategy {

  def removeTypeAnnotation(typeElement: ScTypeElement): Unit = {
    typeElement.prevSiblings
      .find(_.getText == ":")
      .foreach(_.delete())
    typeElement.delete()
  }
}

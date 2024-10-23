package org.jetbrains.plugins.scala.codeInsight.intention.types

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{Sc3TypedPattern, ScTypedPattern, ScTypedPatternLike}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScFunctionExpr, ScUnderscoreSection}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._

class AddOrRemoveStrategy(editor: Option[Editor] = None) extends AddOnlyStrategy(editor) {

  import AddOrRemoveStrategy._

  override def functionWithType(function: ScFunctionDefinition,
                                typeElement: ScTypeElement): Boolean =
    removeTypeAnnotation(typeElement)

  override def valueWithType(value: ScPatternDefinition,
                             typeElement: ScTypeElement): Boolean =
    removeTypeAnnotation(typeElement)

  override def variableWithType(variable: ScVariableDefinition,
                                typeElement: ScTypeElement): Boolean =
    removeTypeAnnotation(typeElement)

  override def patternWithType(pattern: ScTypedPatternLike): Boolean = {
    import pattern.projectContext

    pattern match {
      case p: ScTypedPattern =>

        val newPattern = createPatternFromText(p.name, pattern)
        pattern.replace(newPattern)

        true
      case _: Sc3TypedPattern =>
        val copy = pattern.copy().asInstanceOf[ScTypedPatternLike]
        copy.findFirstChildByType(ScalaTokenTypes.tCOLON).fold(false) { colon =>
          copy.getNode.removeRange(colon.getNode, null)
          val newPattern = createPatternFromText(copy.getText, pattern)
          pattern.replace(newPattern)
          true
        }
      case _ => false
    }
  }

  override def parameterWithType(parameter: ScParameter): Boolean = {
    val caretWasBeforeParameter = editor.exists(_.getCaretModel.getOffset <= parameter.getTextOffset)

    import parameter.projectContext

    val newParameter = createFunctionParameterFromText(parameter.name)

    val pair: Option[(PsiElement, PsiElement)] = parameter.parentOfType(classOf[ScFunctionExpr], strict = false)
      .filter(_.parameters.size == 1)
      .flatMap(_.params.clauses.headOption)
      .filter { clause =>
        val text = clause.getText
        text.startsWith("(") && text.endsWith(")")
      }.map {
      (_, createClauseForFunctionExprFromText(newParameter.getText, parameter))
    }

    val (element, replacement) = pair.getOrElse {
      (parameter, newParameter)
    }

    val replaced = element.replace(replacement)
    // in case caret was here: Seq(1, 2).map((<caret>x: Int) => x.toString)
    // we need it to be here after the action is invoked: Seq(1, 2).map(<caret>x => x.toString)
    if (caretWasBeforeParameter) {
      editor.foreach(_.getCaretModel.moveToOffset(replaced.getTextOffset))
    }

    true
  }

  override def underscoreSectionWithType(underscore: ScUnderscoreSection): Boolean = {
    val caretWasBeforeUnderscore = editor.exists(_.getCaretModel.getOffset <= underscore.getTextOffset)

    // Example: Seq(1, 2).map((_: Int).toString)
    // PSI structure: ExpressionInParenthesis -> TypedExpression -> UnderscoreSection
    val parExpr = underscore.getParent.getParent
    val replaced = parExpr.replace(underscore)

    // in case caret was here: Seq(1, 2).map((<caret>_: Int).toString)
    // we need it to be here after the action is invoked: Seq(1, 2).map(<caret>_.toString)
    if (caretWasBeforeUnderscore) {
      editor.foreach(_.getCaretModel.moveToOffset(replaced.getTextOffset))
    }

    true
  }
}

object AddOrRemoveStrategy {

  def removeTypeAnnotation(typeElement: ScTypeElement): Boolean = {
    typeElement.prevSiblings
      .find(_.textMatches(":"))
      .foreach(_.delete())
    typeElement.delete()

    true
  }
}

package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.hints.{Hint, Text}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScMethodType
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.settings.annotations.Definition
import org.jetbrains.plugins.scala.settings.annotations.Definition.{FunctionDefinition, ValueDefinition, VariableDefinition}

private[codeInsight] trait ScalaTypeHintsPass extends ScalaHintsSettingsHolder {
  import hintsSettings._

  protected def collectTypeHints(editor: Editor, root: PsiElement): Seq[Hint] = {
    if (editor.isOneLineMode || !(showFunctionReturnType || showPropertyType || showLocalVariableType)) Seq.empty
    else {
      for {
        element <- root.elements
        definition = Definition(element)
        (tpe, body) <- typeAndBodyOf(definition)
        if !ScMethodType.hasMethodType(body)
        if showObviousType || !(definition.hasStableType || isTypeObvious(definition.name, tpe, body))
        info <- inlayInfoFor(definition, tpe)(editor.getColorsScheme, TypePresentationContext(element))
      } yield info
    }.toSeq
  }

  private def typeAndBodyOf(definition: Definition): Option[(ScType, ScExpression)] = for {
    body <- definition.bodyCandidate
    tpe <- definition match {
      case ValueDefinition(value) => typeOf(value)
      case VariableDefinition(variable) => typeOf(variable)
      case FunctionDefinition(function) => typeOf(function)
      case _ => None
    }
  } yield (tpe, body)

  private def typeOf(member: ScValueOrVariable): Option[ScType] = {
    val flag = if (member.isLocal) showLocalVariableType else showPropertyType
    if (flag) member.`type`().toOption else None
  }

  private def typeOf(member: ScFunction): Option[ScType] =
    if (showFunctionReturnType) member.returnType.toOption else None


  private def inlayInfoFor(definition: Definition, returnType: ScType)(implicit scheme: EditorColorsScheme, context: TypePresentationContext): Option[Hint] = for {
    anchor <- definition.parameterList
    suffix = definition match {
      case FunctionDefinition(function) if !function.hasAssign && function.hasUnitResultType => Seq(Text(" ="))
      case _ => Seq.empty
    }
    text = Text(": ") +: (textPartsOf(returnType, presentationLength) ++ suffix)
  } yield Hint(text, anchor, suffix = true, menu = typeHintsMenu, relatesToPrecedingElement = true)
}
package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.annotator.hints.{Hint, Text}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScPatternDefinition, ScValueOrVariable, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScMethodType
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.settings.annotations.Definition
import org.jetbrains.plugins.scala.settings.annotations.Definition.{FunctionDefinition, ValueDefinition, VariableDefinition}
import ScalaTypeHintsPass._
import org.jetbrains.plugins.scala.externalHighlighters.ScalaHighlightingMode

private[codeInsight] trait ScalaTypeHintsPass {
  protected implicit def settings: ScalaHintsSettings

  protected def collectTypeHints(editor: Editor, root: PsiElement): Seq[Hint] = {
    val compilerErrorsEnabled = Option(root.getContainingFile).exists { file =>
      file.isScala3File && ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(file)
    }
    if (editor.isOneLineMode ||
      !(settings.showMethodResultType || settings.showMemberVariableType || settings.showLocalVariableType) ||
      compilerErrorsEnabled) {
      Seq.empty
    } else {
      for {
        element <- root.elements
        definition = Definition(element) // NB: "definition" might be in fact _any_ PsiElement (e.g. ScalaFile)
        (tpe, body) <- typeAndBodyOf(definition)
        if !(settings.preserveIndents && (!element.textContains('\n') && definition.hasCustomIndents || adjacentDefinitionsHaveCustomIndent(element)))
        if !ScMethodType.hasMethodType(body)
        if settings.showObviousType || !(definition.hasStableType || isTypeObvious(definition.name, tpe, body))
        info <- hintFor(definition, tpe)(editor.getColorsScheme, TypePresentationContext(element), settings)
      } yield info
    }.toSeq
  }
}

private object ScalaTypeHintsPass {
  private def typeAndBodyOf(definition: Definition)(implicit settings: ScalaHintsSettings): Option[(ScType, ScExpression)] = for {
    body <- definition.bodyCandidate
    tpe <- definition match {
      case ValueDefinition(value) => typeOf(value)
      case VariableDefinition(variable) => typeOf(variable)
      case FunctionDefinition(function) => typeOf(function)
      case _ => None
    }
  } yield (tpe, body)

  private[this] def typeOf(member: ScValueOrVariable)(implicit settings: ScalaHintsSettings): Option[ScType] = {
    val flag = if (member.isLocal) settings.showLocalVariableType else settings.showMemberVariableType
    if (flag) member.`type`().toOption else None
  }

  private[this] def typeOf(member: ScFunction)(implicit settings: ScalaHintsSettings): Option[ScType] =
    if (settings.showMethodResultType) member.returnType.toOption else None

  private def adjacentDefinitionsHaveCustomIndent(definition: PsiElement): Boolean =
    (adjacentDefinitionsFrom(definition.prevSiblings) ++ adjacentDefinitionsFrom(definition.nextSiblings))
      .exists(_.hasCustomIndents)

  private[this] def adjacentDefinitionsFrom(it: Iterator[PsiElement]) = it.grouped(2).collect {
    // can we just use `_: ScDefinitionWithAssignment` here?
    case Seq(ws: PsiWhiteSpace, definition @(_: ScPatternDefinition | _: ScVariableDefinition | _: ScFunctionDefinition))
      if ws.getText.count(_ == '\n') == 1 =>
      Definition(definition)
  }

  private def hintFor(definition: Definition, returnType: ScType)
                     (implicit scheme: EditorColorsScheme, context: TypePresentationContext, settings: ScalaHintsSettings): Option[Hint] =
    for {
      anchor <- definition.parameterList
      suffix = definition match {
        case FunctionDefinition(function) if !function.hasAssign && function.hasUnitResultType => Seq(Text(" ="))
        case _ => Seq.empty
      }
      text = Text(": ") +: (textPartsOf(returnType, settings.presentationLength) ++ suffix)
    } yield Hint(text, anchor, suffix = true, menu = typeHintsMenu, relatesToPrecedingElement = true)
}
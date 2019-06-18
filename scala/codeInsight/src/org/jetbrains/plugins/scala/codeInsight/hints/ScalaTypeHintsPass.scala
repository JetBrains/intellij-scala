package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.annotator.TypeDiff
import org.jetbrains.plugins.scala.annotator.TypeDiff.{Group, Match}
import org.jetbrains.plugins.scala.annotator.hints.{Hint, Text, foldedAttributes, foldedString}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightSettings
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.settings.annotations.Definition
import org.jetbrains.plugins.scala.settings.annotations.Definition.{FunctionDefinition, ValueDefinition, VariableDefinition}

private[codeInsight] trait ScalaTypeHintsPass {
  private val settings = ScalaCodeInsightSettings.getInstance
  import settings._

  protected def collectTypeHints(editor: Editor, root: PsiElement): Seq[Hint] = {
    if (editor.isOneLineMode || !(showFunctionReturnType || showPropertyType || showLocalVariableType)) Seq.empty
    else {
      for {
        element <- root.elements
        val definition = Definition(element)
        (tpe, body) <- typeAndBodyOf(definition)
        if showObviousType || !(definition.hasStableType || isTypeObviousFor(body, tpe.extractClass))
        info <- inlayInfoFor(definition, tpe)(editor.getColorsScheme)
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

  private def isTypeObviousFor(body: ScExpression, maybeClass: Option[PsiClass]): Boolean =
    maybeClass
      .map(_.name -> body)
      .exists {
        case (className, ReferenceName(name, _)) => !name.mismatchesCamelCase(className)
        case _ => false
      }

  private def inlayInfoFor(definition: Definition, returnType: ScType)(implicit scheme: EditorColorsScheme): Option[Hint] = for {
    anchor <- definition.parameterList
    suffix = definition match {
      case FunctionDefinition(function) if !function.hasAssign && function.hasUnitResultType => Seq(Text(" ="))
      case _ => Seq.empty
    }
    text = Text(": ") +: (partsOf(returnType) ++ suffix)
  } yield Hint(text, anchor, suffix = true, menu = Some("TypeHintsMenu"))

  private def partsOf(tpe: ScType)(implicit scheme: EditorColorsScheme): Seq[Text] = {
    def toText(diff: TypeDiff): Text = diff match {
      case Group(diffs) =>
        Text(foldedString,
          foldedAttributes(error = false),
          expansion = Some(() => diffs.map(toText)))
      case Match(text, tpe) =>
        Text(text,
          tooltip = tpe.map(_.canonicalText.replaceFirst("_root_.", "")),
          navigatable = tpe.flatMap(_.extractClass))
    }
    TypeDiff.parse(tpe)
      .flattenTo(maxChars = presentationLength, groupLength = foldedString.length)
      .map(toText)
  }
}

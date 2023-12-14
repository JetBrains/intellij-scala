package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.codeInsight.hints.ParameterHintsPassFactory.forceHintsUpdateOnNextPass
import com.intellij.openapi.actionSystem.{ActionGroup, ActionManager, AnAction, AnActionEvent, Separator}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.annotator.TypeMismatchHints
import org.jetbrains.plugins.scala.annotator.hints.Hint.MenuProvider
import org.jetbrains.plugins.scala.annotator.hints.{Hint, Text}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.codeInsight.hints.ScalaTypeHintsPass._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScReferencePattern, ScTypedPatternLike, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScReferenceExpression, ScTypedExpression, ScUnderscoreSection}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScPatternDefinition, ScValueOrVariable, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScMethodType
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}
import org.jetbrains.plugins.scala.settings.annotations.Definition
import org.jetbrains.plugins.scala.settings.annotations.Definition.{FunctionDefinition, ValueDefinition, VariableDefinition}

private[codeInsight] trait ScalaTypeHintsPass {
  protected implicit def settings: ScalaHintsSettings

  protected def collectTypeHints(editor: Editor, root: PsiElement): Seq[Hint] = {
    val compilerErrorsEnabled = Option(root.getContainingFile).exists { file =>
      file.isScala3File && ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(file)
    }
    if (editor.isOneLineMode ||
      !(settings.showMethodResultType || settings.showMemberVariableType || settings.showLocalVariableType || ScalaHintsSettings.xRayMode && ScalaApplicationSettings.XRAY_SHOW_TYPE_HINTS) ||
      (compilerErrorsEnabled && !ScalaHintsSettings.xRayMode)) {
      Seq.empty
    } else {
      (for {
        element <- root.elements
        definition = Definition(element) // NB: "definition" might be in fact _any_ PsiElement (e.g. ScalaFile)
        (tpe, body, menu) <- typeAndBodyOf(definition)
        if !(settings.preserveIndents && (!element.textContains('\n') && definition.hasCustomIndents || adjacentDefinitionsHaveCustomIndent(element)))
        if !ScMethodType.hasMethodType(body)
        if settings.showObviousType || !(definition.hasStableType || isTypeObvious(definition.name, tpe, body))
        info <- hintFor(definition, tpe, menu)(editor.getColorsScheme, TypePresentationContext(element), settings)
      } yield info) ++ (if (ScalaHintsSettings.xRayMode) collectXRayHints(editor, root) else Seq.empty)
    }.toSeq
  }

  private def collectXRayHints(editor: Editor, root: PsiElement) = root.elements.flatMap {
    case e @ Typeable(t) => xRayHintsFor(e, t)(editor.getColorsScheme, TypePresentationContext(e), settings)
    case _ => Seq.empty
  }

  private def xRayHintsFor(e: PsiElement, t: ScType)(implicit scheme: EditorColorsScheme, context: TypePresentationContext, settings: ScalaHintsSettings): Seq[Hint] = e match {
    case e: ScParameter if e.typeElement.isEmpty && ScalaApplicationSettings.XRAY_SHOW_LAMBDA_PARAMETER_HINTS =>
      hints(e, t, inParentheses = e.getParent.is[ScParameterClause] && e.getParent.getFirstChild.elementType != ScalaTokenTypes.tLPARENTHESIS)
    case e: ScUnderscoreSection if !e.getParent.is[ScTypedExpression] && ScalaApplicationSettings.XRAY_SHOW_LAMBDA_PLACEHOLDER_HINTS =>
      hints(e, t, inParentheses = e.getParent.is[ScReferenceExpression, ScInfixExpr])
    case _: ScReferencePattern | _: ScWildcardPattern if !e.getParent.is[ScPatternList, ScTypedPatternLike] && ScalaApplicationSettings.XRAY_SHOW_VARIABLE_PATTERN_HINTS =>
      hints(e, t, inParentheses = false)
    case _ => Seq.empty
  }

  private def hints(e: PsiElement, t: ScType, inParentheses: Boolean)(implicit scheme: EditorColorsScheme, context: TypePresentationContext, settings: ScalaHintsSettings) = {
    if (inParentheses)
      Seq(Hint(Seq(Text("(")), e, suffix = false), Hint(Text(": ") +: textPartsOf(t, settings.presentationLength, e) :+ Text(")"), e, suffix = true, relatesToPrecedingElement = true))
    else
      Seq(Hint(Text(": ") +: textPartsOf(t, settings.presentationLength, e), e, suffix = true, relatesToPrecedingElement = true))
  }
}

private object ScalaTypeHintsPass {
  private val localVariableTypeContextMenu = disableTypeHintsContextMenu(ScalaCodeInsightBundle.message("disable.type.hints.for.local.variables"), _.showLocalVariableType = false)
  private val memberVariableTypeContextMenu = disableTypeHintsContextMenu(ScalaCodeInsightBundle.message("disable.type.hints.for.member.variables"), _.showPropertyType = false)
  private val methodResultTypeContextMenu = disableTypeHintsContextMenu(ScalaCodeInsightBundle.message("disable.type.hints.for.method.results"), _.showFunctionReturnType = false)

  private def disableTypeHintsContextMenu(@Nls disableText: String, setSettings: ScalaCodeInsightSettings => Unit): MenuProvider = {
    val actionGroup = new ActionGroup() {
      override def getChildren(e: AnActionEvent): Array[AnAction] = Array(
        new AnAction(disableText) {
          override def actionPerformed(e: AnActionEvent): Unit = {
            setSettings(ScalaCodeInsightSettings.getInstance())
            forceHintsUpdateOnNextPass()
            TypeMismatchHints.refreshIn(e.getProject)
          }
        },
        new AnAction(ScalaCodeInsightBundle.message("configure.type.hints")) {
          override def actionPerformed(e: AnActionEvent): Unit = {
            ScalaTypeHintsSettingsModel.navigateTo(e.getProject)
          }
        },
        Separator.getInstance,
        ActionManager.getInstance.getAction(ScalaTypeHintsConfigurable.XRayModeTipAction.Id)
      )
    }

    MenuProvider(actionGroup)
  }

  private def typeAndBodyOf(definition: Definition)(implicit settings: ScalaHintsSettings): Option[(ScType, ScExpression, MenuProvider)] = for {
    body <- definition.bodyCandidate
    (tpe, menu) <- definition match {
      case ValueDefinition(value) => typeOf(value)
      case VariableDefinition(variable) => typeOf(variable)
      case FunctionDefinition(function) => typeOf(function)
      case _ => None
    }
  } yield (tpe, body, menu)

  private[this] def typeOf(member: ScValueOrVariable)(implicit settings: ScalaHintsSettings): Option[(ScType, MenuProvider)] = {
    for {
      menu <- if (member.isLocal) settings.showLocalVariableType.option(localVariableTypeContextMenu)
              else settings.showMemberVariableType.option(memberVariableTypeContextMenu)
      ty <- member.`type`().toOption
    } yield (ty, menu)
  }

  private[this] def typeOf(member: ScFunction)(implicit settings: ScalaHintsSettings): Option[(ScType, MenuProvider)] =
    if (settings.showMethodResultType) member.returnType.toOption.map(_ -> methodResultTypeContextMenu) else None

  private def adjacentDefinitionsHaveCustomIndent(definition: PsiElement): Boolean =
    (adjacentDefinitionsFrom(definition.prevSiblings) ++ adjacentDefinitionsFrom(definition.nextSiblings))
      .exists(_.hasCustomIndents)

  private[this] def adjacentDefinitionsFrom(it: Iterator[PsiElement]) = it.grouped(2).collect {
    // can we just use `_: ScDefinitionWithAssignment` here?
    case Seq(ws: PsiWhiteSpace, definition @(_: ScPatternDefinition | _: ScVariableDefinition | _: ScFunctionDefinition))
      if ws.getText.count(_ == '\n') == 1 =>
      Definition(definition)
  }

  private def hintFor(definition: Definition, returnType: ScType, menu: MenuProvider)
                     (implicit scheme: EditorColorsScheme, context: TypePresentationContext, settings: ScalaHintsSettings): Option[Hint] =
    for {
      anchor <- definition.parameterList
      suffix = definition match {
        case FunctionDefinition(function) if !function.hasAssign && function.hasUnitResultType => Seq(Text(" ="))
        case _ => Seq.empty
      }
      text = Text(": ") +: (textPartsOf(returnType, settings.presentationLength, anchor) ++ suffix)
    } yield Hint(text, anchor, suffix = true, menu, relatesToPrecedingElement = true)
}

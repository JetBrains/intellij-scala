package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.openapi.actionSystem.{ActionGroup, ActionManager, AnAction, AnActionEvent, Separator}
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.annotator.hints.Hint.MenuProvider
import org.jetbrains.plugins.scala.annotator.hints.{Hint, Text}
import org.jetbrains.plugins.scala.codeInsight.hints.ScalaInlayParameterHintsPass._
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHints
import org.jetbrains.plugins.scala.codeInsight.{ScalaCodeInsightBundle, ScalaCodeInsightSettings}
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocQuickInfoGenerator
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

import scala.annotation.tailrec

trait ScalaInlayParameterHintsPass {
  protected implicit def settings: ScalaHintsSettings

  protected def collectParameterHints(editor: Editor, root: PsiElement): Seq[Hint] =
    root.elements.flatMap(getParameterHints).toSeq

  private def getParameterHints(element: PsiElement): Seq[Hint] = {
    val showParameterHints = settings.showParameters

    val showArgumentHints = ScalaHintsSettings.xRayMode && ScalaApplicationSettings.XRAY_SHOW_ARGUMENT_HINTS

    if (!(showParameterHints || showArgumentHints)) return Seq.empty

    val matchedParameters = (element match {
      case e if qualifiedMethodNameFor(e).startsWith("scala.") => Seq.empty
      case call: ScMethodCall => call.matchedParameters
      case invocation: ScConstructorInvocation => invocation.matchedParameters
      case _ => Seq.empty
    }).filter {
      case (argument, _) => element.isAncestorOf(argument)
    }

    (if (showParameterHints) parameterHints(matchedParameters) else Seq.empty) ++
      (if (showArgumentHints) argumentHints(matchedParameters) ++ referenceHints(element) else Seq.empty)
  }

  private def qualifiedMethodNameFor(element: PsiElement): String = element match {
    case ResolveMethodCall(qualifiedMethodName(name)) => name
    case ResolveConstructorCall(qualifiedMethodName(name)) => name
    case _ => ""
  }
}

object ScalaInlayParameterHintsPass {
  private val menu: MenuProvider = MenuProvider(new ActionGroup() {
    override def getChildren(e: AnActionEvent): Array[AnAction] = Array(
      new AnAction(ScalaCodeInsightBundle.message("disable.hints.for.parameter.names")) {
        override def actionPerformed(e: AnActionEvent): Unit = {
          ScalaCodeInsightSettings.getInstance.showParameterNames = false
          ImplicitHints.updateInAllEditors()
        }
      },
      new AnAction(ScalaCodeInsightBundle.message("configure.parameter.name.hints")) {
        override def actionPerformed(e: AnActionEvent): Unit = {
          ScalaParameterHintsSettingsModel.navigateTo(e.getProject)
        }
      },
      Separator.getInstance,
      ActionManager.getInstance.getAction(ScalaTypeHintsConfigurable.XRayModeTipAction.Id)
    )
  })

  private def argumentHints(matchedParameters: Seq[(ScExpression, Parameter)]) = matchedParameters.collect {
    case (argument, parameter) if parameter.isByName =>
      if (argument.is[ScBlockExpr])
        Hint(Seq(Text(" () =>")), argument.getFirstChild, suffix = true)
      else
        Hint(Seq(Text("() => ")), argument, suffix = false)
  }

  private def referenceHints(e: PsiElement) = e match {
    case ResolvesTo(parameter: ScParameter) if parameter.isCallByNameParameter =>
      Seq(Hint(Seq(Text("()")), e, suffix = true))
    case _ =>
      Seq.empty
  }

  private def parameterHints(matchedParameters: Seq[(ScExpression, Parameter)]) = {
    val (varargs, regular) = matchedParameters.partition {
      case (_, parameter) => parameter.isRepeated
    }

    (regular ++ varargs.headOption).filter {
      case (argument, _) if !isNameable(argument) => false
      case (_: ScUnderscoreSection, _) => false
      case (_, parameter) if parameter.name.isEmpty || !ScalaHintsSettings.xRayMode && parameter.name.length == 1 => false
      case (argument, _) => isUnclear(argument)
      case _ => true
    }.map {
      case (argument, parameter) =>
        val tooltip = () => parameter.psiParam.flatMap(p => Option(ScalaDocQuickInfoGenerator.getQuickNavigateInfo(p, argument)))
        Hint(Seq(Text(parameter.name, tooltip = tooltip, navigatable = parameter.psiParam), Text(s" ${ScalaTokenTypes.tASSIGN} ")), argument, suffix = false, menu = menu)
    }
  }

  private object qualifiedMethodName {

    def unapply(method: PsiMethod): Some[String] = {
      val classFqn = method.containingClass match {
        case null => ""
        case clazz => s"${clazz.qualifiedName}."
      }
      Some(classFqn + method.name)
    }
  }

  private object ResolveMethodCall {

    def unapply(call: ScMethodCall): Option[PsiMethod] =
      call.applyOrUpdateElement.collect {
        case ScalaResolveResult(method: PsiMethod, _) => method
      }.orElse {
        call.deepestInvokedExpr match {
          case ResolvesTo(method: PsiMethod) => Some(method)
          case _ => None
        }
      }
  }

  private object ResolveConstructorCall {

    def unapply(constrInvocation: ScConstructorInvocation): Option[PsiMethod] =
      constrInvocation.reference.collect {
        case ResolvesTo(method: PsiMethod) => method
      }
  }

  private[this] def isNameable(argument: ScExpression) =
    argument.getParent match {
      case list: ScArgumentExprList => list.isArgsInParens
      case _ => false
    }

  @tailrec
  private[this] def isUnclear(expression: ScExpression): Boolean = expression match {
    case _: ScLiteral | _: ScThisReference => true
    case ScParenthesisedExpr(inner) => isUnclear(inner)
    case ScSugarCallExpr(base, _, _) => isUnclear(base)
    case _ => false
  }
}

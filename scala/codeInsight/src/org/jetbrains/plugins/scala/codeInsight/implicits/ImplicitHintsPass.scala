package org.jetbrains.plugins.scala
package codeInsight
package implicits

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.util.DocumentUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.annotator.hints._
import org.jetbrains.plugins.scala.annotator.ScalaAnnotator
import org.jetbrains.plugins.scala.annotator.intention.PopupPosition
import org.jetbrains.plugins.scala.annotator.quickfix.ImportImplicitInstanceFix
import org.jetbrains.plugins.scala.codeInsight.hints.methodChains.ScalaMethodChainInlayHintsPass
import org.jetbrains.plugins.scala.codeInsight.hints.ScalaHintsSettings
import org.jetbrains.plugins.scala.codeInsight.hints.ScalaTypeHintsPass
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHintsPass._
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocQuickInfoGenerator
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.externalHighlighters.ScalaHighlightingMode
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructorInvocation
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

private[codeInsight] class ImplicitHintsPass(private val editor: Editor, private val rootElement: ScalaFile, override val settings: ScalaHintsSettings)
  extends EditorBoundHighlightingPass(editor, rootElement.getContainingFile, /*runIntentionPassAfter*/ false)
    with ScalaTypeHintsPass with ScalaMethodChainInlayHintsPass {

  import annotator.hints._

  private var hints: Seq[Hint] = Seq.empty

  override def doCollectInformation(indicator: ProgressIndicator): Unit = {
    hints = Seq.empty

    if (myDocument != null && rootElement.containingVirtualFile.isDefined) {
      // TODO Use a dedicated pass when built-in "advanced" hint API will be available in IDEA, SCL-14502
      rootElement.elements.foreach(e => AnnotatorHints.in(e).foreach(hints ++= _.hints))
      // TODO Use a dedicated pass when built-in "advanced" hint API will be available in IDEA, SCL-14502
      hints ++= collectTypeHints(editor, rootElement)
      collectConversionsAndArguments()
      collectMethodChainHints(editor, rootElement)
    }
  }

  private def collectConversionsAndArguments(): Unit = {
    val settings = ScalaProjectSettings.getInstance(rootElement.getProject)
    val showImplicitErrorsForFile = ScalaAnnotator.isAdvancedHighlightingEnabled(rootElement) &&
      (settings.isShowNotFoundImplicitArguments || settings.isShowAmbiguousImplicitArguments)

    def showImplicitErrors(element: PsiElement) =
      (settings.isShowNotFoundImplicitArguments || settings.isShowAmbiguousImplicitArguments) &&
        ScalaAnnotator.isAdvancedHighlightingEnabled(element)

    if (!ImplicitHints.enabled && !showImplicitErrorsForFile)
      return

    def implicitArgumentsOrErrorHints(owner: ImplicitArgumentsOwner): Seq[Hint] = {
      val showShowImplicitErrors = showImplicitErrors(owner)
      val compilerErrorsEnabled = ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(rootElement)
      val shouldSearch = (ImplicitHints.enabled || showShowImplicitErrors) &&
        !(compilerErrorsEnabled && rootElement.isInScala3Module)

      def shouldShow(arguments: Seq[ScalaResolveResult]) =
        ImplicitHints.enabled || (showShowImplicitErrors && arguments.exists(it =>
          it.isImplicitParameterProblem &&
            (if (probableArgumentsFor(it).size > 1) settings.isShowAmbiguousImplicitArguments
            else settings.isShowNotFoundImplicitArguments)))

      if (shouldSearch) {
        owner.findImplicitArguments.toSeq.flatMap {
          case args if shouldShow(args) =>
            implicitArgumentsHint(owner, args)(editor.getColorsScheme, owner)
          case _ => Seq.empty
        }
      }
      else Seq.empty
    }

    def explicitArgumentHint(e: ImplicitArgumentsOwner): Seq[Hint] = {
      if (!ImplicitHints.enabled) return Seq.empty

      e.explicitImplicitArgList.toSeq
        .flatMap(explicitImplicitArgumentsHint)
    }

    def implicitConversionHints(e: ScExpression): Seq[Hint] = {
      if (!ImplicitHints.enabled) return Seq.empty

      e.implicitConversion().toSeq.flatMap { conversion =>
        implicitConversionHint(e, conversion)(editor.getColorsScheme, e)
      }
    }

    rootElement.depthFirst().foreach {
      case enum@ScEnumerator.withDesugaredAndEnumeratorToken(desugaredEnum, token) =>
        val analogCall = desugaredEnum.analogMethodCall
        def mapBackTo(e: PsiElement)(hint: Hint): Hint = hint.copy(element = e)
        enum match {
          case _: ScForBinding | _: ScGuard =>
            hints ++:= implicitConversionHints(analogCall).map(mapBackTo(enum))
          case _ =>
        }
        hints ++:= implicitArgumentsOrErrorHints(analogCall).map(mapBackTo(token))
      case e: ScExpression =>
        hints ++:= implicitConversionHints(e)
        hints ++:= explicitArgumentHint(e)
        hints ++:= implicitArgumentsOrErrorHints(e)
      case c: ScConstructorInvocation =>
        hints ++:= explicitArgumentHint(c)
        hints ++:= implicitArgumentsOrErrorHints(c)
      case _ =>
    }
  }

  override def doApplyInformationToEditor(): Unit = {
    EditorScrollingPositionKeeper.perform(myEditor, false, () => regenerateHints())

    if (rootElement == myFile) {
      ImplicitHints.setUpToDate(myEditor, myFile)
    }
  }

  private def regenerateHints(): Unit = {
    val inlayModel = myEditor.getInlayModel
    val existingInlays = inlayModel.inlaysIn(rootElement.getTextRange)

    val bulkChange = existingInlays.length + hints.length  > BulkChangeThreshold

    DocumentUtil.executeInBulk(myEditor.getDocument, bulkChange, () => {
      existingInlays.foreach(Disposer.dispose)
      hints.foreach(inlayModel.add(_))
      regenerateMethodChainHints(myEditor, inlayModel, rootElement)
    })
  }
}

private object ImplicitHintsPass {
  import org.jetbrains.plugins.scala.annotator.hints.Hint

  private final val BulkChangeThreshold = 1000

  private def implicitConversionHint(e: ScExpression, conversion: ScalaResolveResult)
                                    (implicit scheme: EditorColorsScheme, owner: ImplicitArgumentsOwner): Seq[Hint] =
    Seq(Hint(namedBasicPresentation(conversion) :+ Text("("), e, suffix = false, menu = Some(menu.ImplicitConversion)),
      Hint(Text(")") +: collapsedPresentationOf(conversion.implicitParameters), e, suffix = true, menu = Some(menu.ImplicitArguments)))

  private def implicitArgumentsHint(e: ImplicitArgumentsOwner, arguments: Seq[ScalaResolveResult])
                                   (implicit scheme: EditorColorsScheme, owner: ImplicitArgumentsOwner): Seq[Hint] = {
    Seq(Hint(presentationOf(arguments), e, suffix = true, menu = Some(menu.ImplicitArguments)))
  }

  private def explicitImplicitArgumentsHint(args: ScArgumentExprList): Seq[Hint] =
    Seq(Hint(Seq(Text(".explicitly")), args, suffix = false, menu = Some(menu.ExplicitArguments)))

  private def presentationOf(arguments: Seq[ScalaResolveResult])
                            (implicit scheme: EditorColorsScheme, owner: ImplicitArgumentsOwner): Seq[Text] = {

    if (!ImplicitHints.enabled)
      collapsedPresentationOf(arguments)
    else
      expandedPresentationOf(arguments)
  }

  private def collapsedPresentationOf(arguments: Seq[ScalaResolveResult])
                                     (implicit scheme: EditorColorsScheme, owner: ImplicitArgumentsOwner): Seq[Text] =
    if (arguments.isEmpty) Seq.empty
    else {
      val problems = arguments.filter(_.isImplicitParameterProblem)
      val folding = Text(foldedString,
        attributes = foldedAttributes(error = problems.nonEmpty),
        expansion = Some(() => expandedPresentationOf(arguments).drop(1).dropRight(1))
      )

      Seq(
        Text("("),
        folding,
        Text(")")
      ).withErrorTooltipIfEmpty(notFoundTooltip(problems))
    }

  private def expandedPresentationOf(arguments: Seq[ScalaResolveResult])
                                    (implicit scheme: EditorColorsScheme, owner: ImplicitArgumentsOwner): Seq[Text] =
    if (arguments.isEmpty) Seq.empty
    else {
      arguments.join(
        Text("("),
        Text(", "),
        Text(")")
      )(presentationOf)
    }

  private def presentationOf(argument: ScalaResolveResult)
                            (implicit scheme: EditorColorsScheme, owner: ImplicitArgumentsOwner): Seq[Text] =
    argument.isImplicitParameterProblem
      .option(problemPresentation(parameter = argument))
      .getOrElse(namedBasicPresentation(argument) ++ collapsedPresentationOf(argument.implicitParameters))

  private def namedBasicPresentation(result: ScalaResolveResult): Seq[Text] = {
    val delegate = result.element match {
      case f: ScFunction => Option(f.syntheticNavigationElement).getOrElse(f)
      case element => element
    }

    val tooltip = ScalaDocQuickInfoGenerator.getQuickNavigateInfo(delegate, delegate, result.substitutor)
    Seq(
      Text(result.name, navigatable = delegate.asOptionOfUnsafe[Navigatable], tooltip = Some(tooltip))
    )
  }

  private def problemPresentation(parameter: ScalaResolveResult)
                                 (implicit scheme: EditorColorsScheme, owner: ImplicitArgumentsOwner): Seq[Text] = {
    probableArgumentsFor(parameter) match {
      case Seq()                                                => noApplicableExpandedPresentation(parameter)
      case Seq((arg, result)) if arg.implicitParameters.isEmpty => presentationOfProbable(arg, result)
      case args                                                 => collapsedProblemPresentation(parameter, args)
    }
  }

  private def noApplicableExpandedPresentation(parameter: ScalaResolveResult)
                                              (implicit scheme: EditorColorsScheme, owner: ImplicitArgumentsOwner) = {

    val qMarkText = Text("?", likeWrongReference, navigatable = parameter.element.asOptionOfUnsafe[Navigatable])
    val paramTypeSuffix = Text(typeSuffix(parameter))

    (qMarkText :: paramTypeSuffix :: Nil)
      .withErrorTooltipIfEmpty(notFoundTooltip(parameter))
  }

  private def collapsedProblemPresentation(parameter: ScalaResolveResult, probableArgs: Seq[(ScalaResolveResult, FullInfoResult)])
                                          (implicit scheme: EditorColorsScheme, owner: ImplicitArgumentsOwner): Seq[Text] = {
    val errorTooltip =
      if (probableArgs.size > 1) ambiguousTooltip(parameter)
      else notFoundTooltip(parameter)

    val presentationString =
      if (!ImplicitHints.enabled) foldedString else foldedString + typeSuffix(parameter)

    Seq(Text(
      presentationString,
      foldedAttributes(error = parameter.isImplicitParameterProblem),
      effectRange = Some((0, foldedString.length)),
      navigatable = parameter.element.asOptionOfUnsafe[Navigatable],
      errorTooltip = Some(errorTooltip),
      expansion = Some(() => expandedProblemPresentation(parameter, probableArgs))
    ))
  }

  private def expandedProblemPresentation(parameter: ScalaResolveResult, arguments: Seq[(ScalaResolveResult, FullInfoResult)])
                                         (implicit scheme: EditorColorsScheme, owner: ImplicitArgumentsOwner): Seq[Text] = {

    arguments match {
      case Seq((arg, result)) => presentationOfProbable(arg, result).withErrorTooltipIfEmpty(notFoundTooltip(parameter))
      case _                  => expandedAmbiguousPresentation(parameter, arguments)
    }
  }

  private def expandedAmbiguousPresentation(parameter: ScalaResolveResult, arguments: Seq[(ScalaResolveResult, FullInfoResult)])
                                           (implicit scheme: EditorColorsScheme, owner: ImplicitArgumentsOwner) =
    arguments.join(Text(" | ", likeWrongReference)) {
      case (argument, result) => presentationOfProbable(argument, result)
    }.withErrorTooltipIfEmpty {
      ambiguousTooltip(parameter)
    }

  private def presentationOfProbable(argument: ScalaResolveResult, result: FullInfoResult)
                                    (implicit scheme: EditorColorsScheme, owner: ImplicitArgumentsOwner): Seq[Text] = {
    result match {
      case OkResult =>
        namedBasicPresentation(argument)

      case ImplicitParameterNotFoundResult =>
        val presentationOfParameters = argument.implicitParameters
          .join(
            Text("("),
            Text(", "),
            Text(")")
          )(presentationOf)
        namedBasicPresentation(argument) ++ presentationOfParameters

      case DivergedImplicitResult =>
        namedBasicPresentation(argument)
          .withErrorTooltipIfEmpty(ScalaCodeInsightBundle.message("implicit.is.diverged"))
          .withAttributes(errorAttributes)

      case CantInferTypeParameterResult =>
        namedBasicPresentation(argument)
          .withErrorTooltipIfEmpty(ScalaCodeInsightBundle.message("can.t.infer.proper.types.for.type.parameters"))
          .withAttributes(errorAttributes)
    }
  }

  private def typeSuffix(parameter: ScalaResolveResult): String = {
    val paramType = parameter.implicitSearchState.map(_.presentableTypeText).getOrElse("NotInferred")
    s": $paramType"
  }

  private def paramWithType(parameter: ScalaResolveResult): String =
    StringUtil.escapeXmlEntities(parameter.name + typeSuffix(parameter))

  private def notFoundTooltip(parameter: ScalaResolveResult)
                             (implicit owner: ImplicitArgumentsOwner): ErrorTooltip = {
    val message = ScalaCodeInsightBundle.message("no.implicits.found.for.parameter", paramWithType(parameter))
    notFoundErrorTooltip(message, Seq(parameter))
  }

  private def notFoundTooltip(parameters: Seq[ScalaResolveResult])
                             (implicit owner: ImplicitArgumentsOwner): Option[ErrorTooltip] = {
    parameters match {
      case Seq()  => None
      case Seq(p) => Some(notFoundTooltip(p))
      case ps     =>
        val message = ScalaCodeInsightBundle.message("no.implicits.found.for.parameters", ps.map(paramWithType).mkString(", "))
        Some(notFoundErrorTooltip(message, ps))
    }
  }

  private def notFoundErrorTooltip(@Nls message: String, notFoundArgs: Seq[ScalaResolveResult])
                                  (implicit owner: ImplicitArgumentsOwner): ErrorTooltip = {
    val quickFix = ImportImplicitInstanceFix(notFoundArgs, owner, PopupPosition.atCustomLocation)
    quickFix match {
      case Some(fix) => ErrorTooltip(message, fix, owner)
      case _         => ErrorTooltip(message)
    }
  }


  private def ambiguousTooltip(parameter: ScalaResolveResult)
                              (implicit owner: ImplicitArgumentsOwner): ErrorTooltip = {
    val message = ScalaCodeInsightBundle.message("ambiguous.implicits.for.parameter", paramWithType(parameter))
    notFoundErrorTooltip(message, Seq(parameter))
  }
}


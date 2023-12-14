package org.jetbrains.plugins.scala.codeInsight.implicits

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
import org.jetbrains.plugins.scala.annotator.HighlightingAdvisor
import org.jetbrains.plugins.scala.annotator.hints._
import org.jetbrains.plugins.scala.autoImport.quickFix.{ImportImplicitInstanceFix, PopupPosition}
import org.jetbrains.plugins.scala.caches.{ModTracker, cachedInUserData}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.codeInsight.hints.methodChains.ScalaMethodChainInlayHintsPass
import org.jetbrains.plugins.scala.codeInsight.hints.rangeHints.RangeInlayHintsPass
import org.jetbrains.plugins.scala.codeInsight.hints.{ScalaHintsSettings, ScalaInlayParameterHintsPass, ScalaTypeHintsPass}
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHintsPass._
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocQuickInfoGenerator
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructorInvocation
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.api.{ImplicitArgumentsOwner, InferUtil, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector._
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider.fromScMethodLike
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.{ScalaHighlightingMode, ScalaProjectSettings}
import org.jetbrains.plugins.scala.extensions.&

import scala.collection.mutable

private[codeInsight]
class ImplicitHintsPass(
  private val editor: Editor,
  private val rootElement: ScalaFile,
  override val settings: ScalaHintsSettings,
  isPreviewPass: Boolean = false, // for the settings preview
) extends EditorBoundHighlightingPass(
  editor,
  rootElement.getContainingFile,
  /*runIntentionPassAfter*/ false
) with ScalaTypeHintsPass
  with ScalaInlayParameterHintsPass
  with ScalaMethodChainInlayHintsPass
  with RangeInlayHintsPass {

  import org.jetbrains.plugins.scala.annotator.hints._

  private val hints: mutable.Buffer[Hint] = mutable.ArrayBuffer.empty

  override def doCollectInformation(indicator: ProgressIndicator): Unit = {
    if (!HighlightingAdvisor.shouldInspect(rootElement) && !isPreviewPass)
      return

    hints.clear()

    if (myDocument != null && (rootElement.containingVirtualFile.isDefined || isPreviewPass)) {
      // TODO Use a dedicated pass when built-in "advanced" hint API will be available in IDEA, SCL-14502
      rootElement.elements.foreach(e => AnnotatorHints.in(e).foreach(hints ++= _.hints))
      // TODO Use a dedicated pass when built-in "advanced" hint API will be available in IDEA, SCL-14502
      hints ++= collectTypeHints(editor, rootElement)
      hints ++= collectParameterHints(editor, rootElement)
      collectConversionsAndArguments()
      collectMethodChainHints(editor, rootElement)
      collectRangeHints(editor, rootElement)
    }
  }

  private def collectConversionsAndArguments(): Unit = {
    val settings = ScalaProjectSettings.getInstance(rootElement.getProject)
    val showImplicitErrorsForFile = HighlightingAdvisor.isTypeAwareHighlightingEnabled(rootElement) &&
      (settings.isShowNotFoundImplicitArguments || settings.isShowAmbiguousImplicitArguments)

    def showImplicitErrors(element: PsiElement) =
      (settings.isShowNotFoundImplicitArguments || settings.isShowAmbiguousImplicitArguments) &&
        HighlightingAdvisor.isTypeAwareHighlightingEnabled(element)

    if (!ImplicitHints.enabled && !showImplicitErrorsForFile)
      return

    def shouldShowImplicitArgumentsOrErrors(enabledForElement: Boolean, arguments: Seq[ScalaResolveResult]): Boolean =
      ImplicitHints.enabled || (enabledForElement && arguments.exists(it =>
        it.isImplicitParameterProblem &&
          (if (probableArgumentsFor(it).size > 1) settings.isShowAmbiguousImplicitArguments
           else settings.isShowNotFoundImplicitArguments)
      ))

    def shouldSearchForImplicits(enabledForElement: Boolean): Boolean = {
      val compilerErrorsEnabled = ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(rootElement)

      (ImplicitHints.enabled || enabledForElement) &&
        !(compilerErrorsEnabled && rootElement.isInScala3Module && !ScalaHintsSettings.xRayMode)
    }

    def implicitArgumentsOrErrorHints(owner: ImplicitArgumentsOwner): Seq[Hint] = {
      val enabledForElement = showImplicitErrors(owner)

      if (shouldSearchForImplicits(enabledForElement)) {
        owner.findImplicitArguments.toSeq.flatMap {
          case args if shouldShowImplicitArgumentsOrErrors(enabledForElement, args) =>
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

    def implicitConversionHints(expression: ScExpression): Seq[Hint] = {
      if (!ImplicitHints.enabled) return Seq.empty

      val implicitConversion = expression.implicitConversion()
      implicitConversion.toSeq.flatMap { conversion =>
        implicitConversionHint(expression, conversion)(editor.getColorsScheme, expression)
      }
    }

    def injectedConstructorHintsFor(tdef: ScTemplateDefinition): Seq[Hint] = {
      val enabledForElement = showImplicitErrors(tdef)

      if (!tdef.isInScala3File)                              Seq.empty
      else if (!shouldSearchForImplicits(enabledForElement)) Seq.empty
      else {
        val injectedConstructors = tdef.injectedParentTraitConstructorCalls
        val anchor               = tdef.extendsBlock.templateParents.getOrElse(tdef.extendsBlock)

        if (injectedConstructors.isEmpty) Seq.empty
        else
          cachedInUserData(
            "injectedConstructorHintsFor",
            tdef,
            ModTracker.physicalPsiChange(tdef.getProject)
          ) {
            val consNames = injectedConstructors.toSeq.flatMap { case (cons, subst) =>
              val name = cons.name

              val (_, args, _) = InferUtil.updateTypeWithImplicitParameters(
                cons.polymorphicType(subst),
                anchor,
                None,
                canThrowSCE = false,
                fullInfo    = false
              )

              val implicitArgs = args.getOrElse(Seq.empty)

              if (shouldShowImplicitArgumentsOrErrors(enabledForElement, implicitArgs)) {
                Text(s" with $name") +: presentationOf(implicitArgs, None)(editor.getColorsScheme)
              } else Seq.empty
            }

            val hint = Hint(consNames, anchor, suffix = true)
            Seq(hint)
          }
      }
    }

    rootElement.depthFirst().foreach {
      case (_: ScTemplateParents) & ChildOf(ChildOf(tdef: ScTemplateDefinition)) if !tdef.is[ScTrait] =>
        val parents =
          tdef
            .extendsBlock
            .templateParents
            .fold(Seq.empty[ScConstructorInvocation])(_.parentClauses)

        hints ++= parents.flatMap(explicitArgumentHint)
        hints ++= parents.flatMap(implicitArgumentsOrErrorHints)
        hints ++= injectedConstructorHintsFor(tdef)
      case enumerator@ScEnumerator.withDesugaredAndEnumeratorToken(desugaredEnum, token) =>
        val analogCall = desugaredEnum.analogMethodCall
        def mapBackTo(e: PsiElement)(hint: Hint): Hint = hint.copy(element = e)
        enumerator match {
          case _: ScForBinding | _: ScGuard =>
            hints ++= implicitConversionHints(analogCall).map(mapBackTo(enumerator))
          case _ =>
        }
        hints ++= implicitArgumentsOrErrorHints(analogCall).map(mapBackTo(token))
      case e: ScExpression =>
        hints ++= implicitConversionHints(e)
        hints ++= explicitArgumentHint(e)
        hints ++= implicitArgumentsOrErrorHints(e)
      case _ =>
    }

    ()
  }

  override def doApplyInformationToEditor(): Unit = {
    val runnable: Runnable = () => regenerateHints()
    EditorScrollingPositionKeeper.perform(myEditor, false, runnable)

    if (rootElement == myFile) {
      ImplicitHints.setUpToDate(myEditor, myFile)
    }
  }

  private def regenerateHints(): Unit = {
    import org.jetbrains.plugins.scala.codeInsight.implicits.Model
    val inlayModel = myEditor.getInlayModel
    val existingInlays = inlayModel.inlaysIn(rootElement.getTextRange)

    val bulkChange = existingInlays.length + hints.length > BulkChangeThreshold

    DocumentUtil.executeInBulk(myEditor.getDocument, bulkChange, () => {
      existingInlays.foreach(Disposer.dispose)
      hints.foreach(inlayModel.add(_))
      regenerateMethodChainHints(myEditor, inlayModel, rootElement)
      regenerateRangeInlayHints(myEditor, inlayModel, rootElement)
    })
  }
}

private object ImplicitHintsPass {
  import org.jetbrains.plugins.scala.annotator.hints.Hint

  private final val BulkChangeThreshold = 1000

  private def implicitConversionHint(
    e:          ScExpression,
    conversion: ScalaResolveResult
  )(implicit
    scheme: EditorColorsScheme,
    owner:  ImplicitArgumentsOwner
  ): Seq[Hint] = {
    val hintPrefix =
      Hint(namedBasicPresentation(conversion) :+ Text("("), e, suffix = false, menu = menu.ImplicitConversion)

    val hintSuffix = Hint(
      Text(")") +: collapsedPresentationOf(conversion.implicitParameters, Option(owner)),
      e,
      suffix = true,
      menu = menu.ImplicitArguments
    )

    Seq(hintPrefix, hintSuffix)
  }

  private def implicitArgumentsHint(e: ImplicitArgumentsOwner, arguments: Seq[ScalaResolveResult])
                                   (implicit scheme: EditorColorsScheme, owner: ImplicitArgumentsOwner): Seq[Hint] = {
    val hint = Hint(presentationOf(arguments, Option(owner)), e, suffix = true, menu = menu.ImplicitArguments)
    Seq(hint)
  }

  private def explicitImplicitArgumentsHint(args: ScArgumentExprList): Seq[Hint] = {
    val hint = Hint(Seq(Text(".explicitly")), args, suffix = false, menu = menu.ExplicitArguments)
    Seq(hint)
  }

  private def presentationOf(arguments: Seq[ScalaResolveResult], owner: Option[ImplicitArgumentsOwner])
                            (implicit scheme: EditorColorsScheme): Seq[Text] = {

    if (!ImplicitHints.enabled)
      collapsedPresentationOf(arguments, owner)
    else
      expandedPresentationOf(arguments, owner)
  }

  private def collapsedPresentationOf(arguments: Seq[ScalaResolveResult], owner: Option[ImplicitArgumentsOwner])
                                     (implicit scheme: EditorColorsScheme): Seq[Text] =
    if (arguments.isEmpty) Seq.empty
    else {
      val problems = arguments.filter(_.isImplicitParameterProblem)
      val folding = Text(foldedString,
        attributes = foldedAttributes(error = problems.nonEmpty),
        expansion = Some(() => expandedPresentationOf(arguments, owner).drop(1).dropRight(1))
      )

      Seq(
        Text("("),
        folding,
        Text(")")
      ).withErrorTooltipIfEmpty(notFoundTooltip(problems, owner))
    }

  private def expandedPresentationOf(arguments: Seq[ScalaResolveResult], owner: Option[ImplicitArgumentsOwner])
                                    (implicit scheme: EditorColorsScheme): Seq[Text] =
    if (arguments.isEmpty) Seq.empty
    else {
      arguments.join(
        Text("("),
        Text(", "),
        Text(")")
      )(presentationOf(_, owner))
    }

  private def presentationOf(argument: ScalaResolveResult, owner: Option[ImplicitArgumentsOwner])
                            (implicit scheme: EditorColorsScheme): Seq[Text] = {
    val result = argument.isImplicitParameterProblem
      .option(problemPresentation(parameter = argument, owner))
      .getOrElse(namedBasicPresentation(argument) ++ collapsedPresentationOf(argument.implicitParameters, owner))
    result
  }

  private def namedBasicPresentation(result: ScalaResolveResult): Seq[Text] = {
    val delegate = result.element match {
      case f: ScFunction => Option(f.syntheticNavigationElement).getOrElse(f)
      case element => element
    }

    val tooltip = () => Option(ScalaDocQuickInfoGenerator.getQuickNavigateInfo(delegate, delegate, result.substitutor))
    Seq(
      Text(result.name, navigatable = delegate.asOptionOfUnsafe[Navigatable], tooltip = tooltip)
    )
  }

  private def problemPresentation(parameter: ScalaResolveResult, owner: Option[ImplicitArgumentsOwner])
                                 (implicit scheme: EditorColorsScheme): Seq[Text] = {
    probableArgumentsFor(parameter) match {
      case Seq()                                                => noApplicableExpandedPresentation(parameter, owner)
      case Seq((arg, result)) if arg.implicitParameters.isEmpty => presentationOfProbable(arg, result, owner)
      case args                                                 => collapsedProblemPresentation(parameter, args, owner)
    }
  }

  private def noApplicableExpandedPresentation(parameter: ScalaResolveResult, owner: Option[ImplicitArgumentsOwner])
                                              (implicit scheme: EditorColorsScheme) = {

    val qMarkText = Text("?", likeWrongReference, navigatable = parameter.element.asOptionOfUnsafe[Navigatable])
    val paramTypeSuffix = Text(typeSuffix(parameter))

    (qMarkText :: paramTypeSuffix :: Nil)
      .withErrorTooltipIfEmpty(notFoundTooltip(parameter, owner))
  }

  private def collapsedProblemPresentation(
    parameter:    ScalaResolveResult,
    probableArgs: Seq[(ScalaResolveResult, FullInfoResult)],
    owner:        Option[ImplicitArgumentsOwner]
  )(implicit
    scheme: EditorColorsScheme,
  ): Seq[Text] = {
    val errorTooltip =
      if (probableArgs.size > 1) ambiguousTooltip(parameter, owner)
      else                       notFoundTooltip(parameter, owner)

    val presentationString =
      if (!ImplicitHints.enabled) foldedString else foldedString + typeSuffix(parameter)

    Seq(
      Text(
        presentationString,
        foldedAttributes(error = parameter.isImplicitParameterProblem),
        effectRange = Some((0, foldedString.length)),
        navigatable = parameter.element.asOptionOfUnsafe[Navigatable],
        errorTooltip = Some(errorTooltip),
        expansion = Some(() => expandedProblemPresentation(parameter, probableArgs, owner))
      )
    )
  }

  private def expandedProblemPresentation(
    parameter: ScalaResolveResult,
    arguments: Seq[(ScalaResolveResult, FullInfoResult)],
    owner:     Option[ImplicitArgumentsOwner]
  )(implicit
    scheme: EditorColorsScheme,
  ): Seq[Text] =
    arguments match {
      case Seq((arg, result)) =>
        presentationOfProbable(arg, result, owner)
          .withErrorTooltipIfEmpty(notFoundTooltip(parameter, owner))
      case _ => expandedAmbiguousPresentation(parameter, arguments, owner)
    }

  private def expandedAmbiguousPresentation(
    parameter: ScalaResolveResult,
    arguments: Seq[(ScalaResolveResult, FullInfoResult)],
    owner:     Option[ImplicitArgumentsOwner]
  )(implicit
    scheme: EditorColorsScheme
  ) =
    arguments
      .join(Text(" | ", likeWrongReference)) { case (argument, result) =>
        presentationOfProbable(argument, result, owner)
      }
      .withErrorTooltipIfEmpty {
        ambiguousTooltip(parameter, owner)
      }

  private def presentationOfProbable(argument: ScalaResolveResult, result: FullInfoResult, owner: Option[ImplicitArgumentsOwner])
                                    (implicit scheme: EditorColorsScheme): Seq[Text] = {
    result match {
      case OkResult =>
        namedBasicPresentation(argument)

      case ImplicitParameterNotFoundResult =>
        val presentationOfParameters = argument.implicitParameters
          .join(
            Text("("),
            Text(", "),
            Text(")")
          )(presentationOf(_, owner))
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

  private def notFoundTooltip(parameter: ScalaResolveResult, owner: Option[ImplicitArgumentsOwner]): ErrorTooltip = {
    val message = ScalaCodeInsightBundle.message("no.implicits.found.for.parameter", paramWithType(parameter))
    notFoundErrorTooltip(message, Seq(parameter), owner)
  }

  private def notFoundTooltip(
    parameters: Seq[ScalaResolveResult],
    owner:      Option[ImplicitArgumentsOwner]
  ): Option[ErrorTooltip] =
    parameters match {
      case Seq()  => None
      case Seq(p) => Some(notFoundTooltip(p, owner))
      case ps =>
        val message =
          ScalaCodeInsightBundle.message("no.implicits.found.for.parameters", ps.map(paramWithType).mkString(", "))
        Some(notFoundErrorTooltip(message, ps, owner))
    }

  private def notFoundErrorTooltip(
    @Nls message: String,
    notFoundArgs: Seq[ScalaResolveResult],
    owner:        Option[ImplicitArgumentsOwner]
  ): ErrorTooltip = {
    owner.fold(ErrorTooltip.fromString(message)) { owner =>
      val quickFix = ImportImplicitInstanceFix(() => notFoundArgs, owner, PopupPosition.atCustomLocation)
      ErrorTooltip(message, quickFix, owner)
    }
  }

  private def ambiguousTooltip(parameter: ScalaResolveResult, owner: Option[ImplicitArgumentsOwner]): ErrorTooltip = {
    val message = ScalaCodeInsightBundle.message("ambiguous.implicits.for.parameter", paramWithType(parameter))
    notFoundErrorTooltip(message, Seq(parameter), owner)
  }
}


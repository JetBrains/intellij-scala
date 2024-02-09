package org.jetbrains.plugins.scala.lang.refactoring.inline

import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.format.{AnyStringParser, Injection, InterpolatedStringFormatter, InterpolatedStringParser, StringPart}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createStringLiteralFromText
import org.jetbrains.plugins.scala.lang.refactoring.inline.ScalaInliner.InlineState
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil._
import org.jetbrains.plugins.scala.util.{MultilineStringSettings, MultilineStringUtil}

import scala.jdk.CollectionConverters._

/**
 * ATTENTION: this class is not thread safe.<br>
 * It keeps state of inlining process. Single instance should be used for a single inline operation.
 */
private[inline]
final class ScalaInliner {

  private var inlineState: InlineState = InlineState.empty

  def inlineUsages(usages: Seq[UsageInfo], referenced: PsiElement): InlineState = {
    val usagesGroupedByParentStringLiteral: Map[Option[ScInterpolatedStringLiteral], Seq[UsageInfo]] =
      usages.groupBy(_.getElement.asOptionOf[ScReferenceExpression].flatMap(isInjectionIn.unapply))

    val usagesRegular: Seq[UsageInfo] =
      usagesGroupedByParentStringLiteral.get(None).toSeq.flatten
    val usagesInInterpolatedStringInjectors: Seq[(ScInterpolatedStringLiteral, Seq[UsageInfo])] =
      usagesGroupedByParentStringLiteral.toSeq.collect { case Some(string) -> usages => string -> usages }

    // 1. First inline all non-injector usages
    // These inlines will not break original pointers in the usages.
    usagesRegular.foreach { usage =>
      inlineRegularUsages(usage, referenced)
    }

    // 2. Then handle all usages in injectors (note, it's not actual to injectors with block expression, like s"${value}")
    // Injections in same interpolated string literal should be inlined in a single pass.
    // For every inlined usage, original string literal is replaced with a new one,
    // which is very different from the original (e.g. margins are dropped)
    // If we don't do it in one pass for all injectors, smart pointers,
    // pointing to references in injectors in interpolated string will be invalidated.
    usagesInInterpolatedStringInjectors.foreach { case (string, usages) =>
      //should be safe to cast to ScExpression, we already checked it above
      val expressions = usages.map(_.getElement.asInstanceOf[ScReferenceExpression])
      val replacements: Map[ScReferenceExpression, ScExpression] = expressions
        .map(ref => (ref, createReplacementForSimpleReference(referenced)))
        .collect { case (k, Some(v)) => k -> v}.toMap

      replaceInsideInterpolatedString(string, replacements)
    }

    inlineState
  }

  /**
   * Inline usages which ARE NOT references in string interpolation injector, like s"Hello $name"
   */
  private def inlineRegularUsages(
    usageInfo: UsageInfo,
    referenced: PsiElement
  ): Unit = {
    val usageElement = usageInfo.getElement
    if (usageElement == null) {
      //throw exception and expect that original definition won't be deleted
      throw new AssertionError("Unexpected null element in usage")
    }

    usageElement match {
      case Parent(call: ScMethodCall) =>
        replaceMethodCall(call, referenced)
      case ref: ScReferenceExpression =>
        val replacementExpr = createReplacementForSimpleReference(referenced)
        replacementExpr.map(ref.replaceExpression)
      case Parent(typeElement: ScTypeElement) =>
        replaceTypeElement(typeElement, referenced)
      case _ =>
    }
  }

  private def replaceMethodCall(
    usageMethodCall: ScMethodCall,
    referenced: PsiElement
  ): Unit =
    referenced match {
      case function: ScFunctionDefinition =>
        val replacement = createReplacementForMethodCall(usageMethodCall, function)
        val replacementActual = usageMethodCall.replaceExpression(replacement) //TODO: choose one technique

        //We need to restore string references in order to restore their margins later.
        //The original references were destroyed by `usageMethodCall.replaceExpression` call, because it makes a copy of the element
        val stringsInReplacement = replacement.elements.filterByType[ScStringLiteral]
        val stringsInActual = replacementActual.elements.filterByType[ScStringLiteral]
        stringsInReplacement.zip(stringsInActual).foreach { case (oldLiteral, newLiteral) =>
          inlineState = inlineState.updateReferenceIfExists(oldLiteral, newLiteral)
        }

      case bp: ScBindingPattern =>
        val replacement = createReplacementForPattern(bp)
        replacement.map { replacement =>
          val invokedExpr = usageMethodCall.getInvokedExpr
          invokedExpr.replaceExpression(replacement)
        }

      case _ =>
    }

  /**
   * Examples: {{{
   *    ---- BEFORE
   *    def foo(p: String) = p + p.reverse
   *    foo("test")
   *    ---- AFTER (replacement)
   *    "test" + "test".reverse
   * }}}
   * {{{
   *    ---- BEFORE
   *    def foo(p: String) = p + raw"""$p ${p}"""
   *    foo("test")
   *    ---- AFTER (replacement)
   *    "test" + raw"""test ${"test"}"""
   * }}}
   */
  private def createReplacementForMethodCall(
    usageMethodCall: ScMethodCall,
    inlinedMethodDefinition: ScFunctionDefinition
  ): ScExpression = {
    val inlinedBodyCopy = inlinedMethodDefinition.body match {
      case Some(body) =>
        //NOTE: we add extra braces around the body to simplify the logic of replacement of parameter usages inside.
        //Without extra braces it might happen that the entire `inlinedBodyCopy` is replaced and invalidated completely.
        //With extra braces we can be sure that `inlinedBodyCopy` will not be invalidated and contain all replacements
        //NOTE: later all redundant braces and parentheses are removed from the resulting body
        val bodyTextWithExtraBraces = "{" + body.getText + "}"
        ScalaPsiElementFactory.createExpressionWithContextFromText(bodyTextWithExtraBraces, inlinedMethodDefinition, body)
      case _ =>
        return usageMethodCall
    }

    val paramToReplacement: Seq[(ScParameter, ScExpression)] =
      usageMethodCall.matchedParameters.flatMap {
        case (expr, p) => p.paramInCode.map((_, expr))
      }

    val scope = new LocalSearchScope(inlinedBodyCopy)
    val parameterRefToReplacement: Map[ScReferenceExpression, ScExpression] =
      paramToReplacement.flatMap { case (param, expr) =>
        val referencesToParameter = ReferencesSearch.search(param, scope).asScala.filterByType[ScReferenceExpression]
        referencesToParameter.map(ref => (ref, expr))
      }.toMap

    doReplacementForMethodCall(parameterRefToReplacement)

    unparExpr(inlinedBodyCopy)
  }

  /**
   * NOTE: see the comments inside [[inlineUsages]], the logic inside this method has the same reasoning
   *
   * @param paramRefToReplacement map from parameter usage reference to the actual argument,
   *                              which is used on a inlined method call site
   */
  private def doReplacementForMethodCall(
    paramRefToReplacement: Map[ScReferenceExpression, ScExpression]
  ): Unit = {
    val replacementsGroupedByParentStringLiteral: Map[Option[ScInterpolatedStringLiteral], Map[ScReferenceExpression, ScExpression]] =
      paramRefToReplacement.groupBy(t => isInjectionIn.unapply(t._1))

    val replacementsRegular: Map[ScReferenceExpression, ScExpression] =
      replacementsGroupedByParentStringLiteral.getOrElse(None, Map.empty)
    val replacementsInInterpolatedStringInjectors: Map[ScInterpolatedStringLiteral, Map[ScReferenceExpression, ScExpression]] =
      replacementsGroupedByParentStringLiteral.collect { case Some(string) -> map => string -> map  }

    replacementsRegular.foreach { case (oldExpr, newExpr) =>
      oldExpr.replaceExpression(newExpr)
    }

    replacementsInInterpolatedStringInjectors.foreach{ case (string, replacements) =>
      replaceInsideInterpolatedString(string, replacements)
    }
  }

  private def createReplacementForSimpleReference(referenced: PsiElement): Option[ScExpression] =
    referenced match {
      case bp: ScBindingPattern =>
        createReplacementForPattern(bp)
      case funDef: ScFunctionDefinition =>
        assert(funDef.parameters.isEmpty, "Unexpected function parameters")
        funDef.body.map(unparExpr)
      case _ =>
        None
    }

  private def replaceInsideInterpolatedString(
    string: ScInterpolatedStringLiteral,
    replacements: Map[ScReferenceExpression, ScExpression],
  ): Unit = {
    val marginCharOriginal = MultilineStringUtil.detectMarginChar(string)
    val marginCharToUse = marginCharOriginal.orElse {
      if (!hasLineBreaks(string) && new MultilineStringSettings(string.getProject).insertMargin)
        Some(MultilineStringUtil.DefaultMarginChar)
      else
        None
    }

    val newString = createNewStringWithInjectionsReplaced(string, replacements)
    val newStringReplaced = string.replaceExpression(newString).asInstanceOf[ScStringLiteral]

    if (string.isMultiLineString) {
      inlineState = inlineState.replace(string, newStringReplaced, marginCharToUse)
    }
  }

  private def hasLineBreaks(literal: ScInterpolatedStringLiteral): Boolean =
    literal.getText.contains("\n")

  private def replaceTypeElement(
    usageTypeElement: ScTypeElement,
    referenced: PsiElement
  ): Option[PsiElement] = {
    referenced match {
      case ta: ScTypeAliasDefinition =>
        val aliasedTypeElement = ta.aliasedTypeElement
        aliasedTypeElement.map(usageTypeElement.replace)
      case _ =>
        None
    }
  }

  private def createReplacementForPattern(pattern: ScBindingPattern): Option[ScExpression] =
    PsiTreeUtil.getParentOfType(pattern, classOf[ScDeclaredElementsHolder]) match {
      case v@ScPatternDefinition.expr(e) if v.declaredElements == Seq(pattern) => Some(unparExpr(e))
      case v@ScVariableDefinition.expr(e) if v.declaredElements == Seq(pattern) => Some(unparExpr(e))
      case _ => None
    }

  private object isInjectionIn {
    def unapply(ref: ScExpression): Option[ScInterpolatedStringLiteral] = ref match {
      case Parent(literal: ScInterpolatedStringLiteral) if !literal.reference.contains(ref) =>
        Some(literal)
      case _ =>
        None
    }
  }

  private def createNewStringWithInjectionsReplaced(
    targetString: ScInterpolatedStringLiteral,
    refToReplacement: Map[ScReferenceExpression, ScExpression]
  ): ScStringLiteral = {
    val targetStringParts = InterpolatedStringParser.parse(targetString)
    targetStringParts match {
      case Some(parts) =>
        createNewStringWithInjectionsReplaced(targetString, parts, refToReplacement)
      case _ =>
        targetString
    }
  }

  private def createNewStringWithInjectionsReplaced(
    targetString: ScInterpolatedStringLiteral,
    parts: Seq[StringPart],
    refToReplacement: Map[ScReferenceExpression, ScExpression]
  ): ScStringLiteral = {
    val newParts = parts.flatMap {
      case Injection(ref: ScReferenceExpression, s) =>
        val replacement = refToReplacement.getOrElse(ref, ref)
        val sourceStringInlinedParts = AnyStringParser.parse(replacement)
        sourceStringInlinedParts.getOrElse {
          Seq(Injection(replacement, s))
        }
      case part =>
        Seq(part)
    }

    // Requirement 1:
    // We need to preserve interpolator kind of original string, before inlining
    //
    // Requirement 2:
    // If the string literal was multiline, it should remain multiline - don't break the formatting
    //
    // Requirement 3:
    // If the original string is "raw" we don't want to make it non-interpolated even if there are zero injections left
    // This is because it can contain some characters which would be escaped in a non-interpolated version
    // Suppose we had this:
    //   val name = "MyName"
    //   raw"Hello \w $name"
    // if we removed the raw interpolator we would get
    //   "Hello \\w MyName"
    // (notice how `\` became escaped
    val interpolatorName = targetString.reference.map(_.refName)
    val kind = interpolatorName
      .map(ScInterpolatedStringLiteral.Kind.fromPrefix)
      .getOrElse(ScInterpolatedStringLiteral.Standard)

    val enforceInterpolator = kind == ScInterpolatedStringLiteral.Raw

    val newStringText = InterpolatedStringFormatter(kind).format(
      newParts,
      toMultiline = targetString.isMultiLineString,
      enforceInterpolator = enforceInterpolator
    )
    createStringLiteralFromText(newStringText, targetString)(targetString.projectContext)
  }
}

private[inline]
object ScalaInliner {
  /**
   * @param newStringLiteralToOriginalMarginChar "new target string literal" -> "original target string literal margin"<br>
   *                                             "new" means the one which was before inlining any expressions into the string.
   *                                             After each inline, the string is replaced with a new version.
   *                                             The type is [[ScStringLiteral]] (not interpolated) because the new string
   *                                             can become non-interpolated if if contains zero injections
   */
  case class InlineState(newStringLiteralToOriginalMarginChar: Map[ScStringLiteral, Option[Char]]) {
    def replace(
      oldString: ScStringLiteral,
      newString: ScStringLiteral,
      marginChar: Option[Char]
    ): InlineState = {
      val oldStringRemoved = newStringLiteralToOriginalMarginChar - oldString
      val newStringAdded = oldStringRemoved + (newString -> marginChar)
      InlineState(newStringAdded)
    }

    def updateReferenceIfExists(
      oldString: ScStringLiteral,
      newString: ScStringLiteral,
    ): InlineState = {
      newStringLiteralToOriginalMarginChar.get(oldString) match {
        case Some(value) => replace(oldString, newString, value)
        case None => this
      }
    }
  }

  object InlineState {
    def empty: InlineState = InlineState(Map.empty)
  }
}

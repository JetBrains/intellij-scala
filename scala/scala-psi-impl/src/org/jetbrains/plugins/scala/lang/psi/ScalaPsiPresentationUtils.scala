package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi._
import com.intellij.psi.util.{PsiFormatUtil, PsiFormatUtilBase}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScSignatureClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.{Context, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation._

// TODO 2: unify with org.jetbrains.plugins.scala.lang.psi.PresentationUtil
// TODO 4: unify with org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
// TODO 5: unify with com.intellij.psi.util.PsiFormatUtil
object ScalaPsiPresentationUtils {

  // The Override/Implement renderer has a separate UI contract and formats a
  // `PhysicalMethodSignature`. A future minor cleanup may share individual
  // presentation primitives with it, but must not directly unify the renderers.
  /**
   * Produces the long receiver-qualified primary label for a Scala 3 extension
   * method, including its type, term, and contextual signature clauses.
   *
   * For example:
   * {{{
   * extension (target: User)
   *   def present(suffix: String): String = ???
   * }}}
   * is rendered as `User.present(suffix: String)`.
   *
   * The global Scala PSI presentation, the legacy Go To target renderer, and
   * the long Find/Show Usages labels use this text. The global presentation
   * deliberately remains long because it is the current Scala-plugin-only
   * fallback for the modern Go To Declaration popup.
   */
  private[scala] def extensionMethodPresentableText(function: ScFunction): String = {
    val shortText = extensionMethodShortText(function)
    val signatureText = function.signatureClauses.map {
      case ScSignatureClause.TypeClause(clause) => clause.getText
      case ScSignatureClause.TermClause(clause) => clause.getText
    }.mkString
    shortText + signatureText
  }

  /**
   * Produces the short receiver-qualified label for a Scala 3 extension
   * method, without its signature clauses.
   *
   * For example:
   * {{{
   * extension (target: User)
   *   def present(suffix: String): String = ???
   * }}}
   * is rendered as `User.present`.
   *
   * Usage View short names and declaration grouping use this form, matching
   * ordinary methods' compact labels while keeping the receiver needed to
   * distinguish extension overloads.
   */
  private[scala] def extensionMethodShortText(function: ScFunction): String =
    function.extensionMethodOwner
      .flatMap(_.targetTypeElement)
      .map(receiverType => s"${receiverType.getText}.${function.name}")
      .getOrElse(function.name)

  /**
   * Returns the source container shown separately from an extension method's
   * receiver-qualified primary label.
   *
   * For an extension in `demo.extensions.Definitions`, this returns
   * `Some("demo.extensions.Definitions")`; a top-level extension falls back
   * to its package qualifier.
   *
   * The global Scala PSI presentation and the legacy Go To target renderer
   * use this as the navigation location text.
   */
  private[scala] def extensionMethodContainerText(function: ScFunction): Option[String] =
    Option(function.containingClass)
      .flatMap(sourceContainerText)
      .orElse(function.topLevelQualifier)

  /**
   * Returns a source-facing name for a class-like PSI container.
   *
   * For example, `object Definitions` in package `demo.extensions` returns
   * `Some("demo.extensions.Definitions")`, rather than the synthetic JVM name
   * `Definitions$`.
   *
   * Extension container presentation uses this helper, as do ordinary Scala
   * named-element locations backed by a type definition.
   */
  private[scala] def sourceContainerText(psiClass: PsiClass): Option[String] =
    psiClass match {
      case typeDefinition: ScTypeDefinition =>
        Option(typeDefinition.qualifiedName).orElse(Option(typeDefinition.name))
      case _ =>
        Option(psiClass.getQualifiedName).orElse(Option(psiClass.getName))
    }

  def methodPresentableText(
    method: PsiMethod,
  ): String = {
    implicit val tpc: TypePresentationContext = TypePresentationContext(method)
    implicit val context: Context = Context(method)

    method match {
      case function: ScFunction =>
        FunctionRenderer.simple(_.presentableText).render(function)
      case _ =>
        import PsiFormatUtilBase._
        val pramOptions = SHOW_NAME | SHOW_TYPE | TYPE_AFTER
        PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, pramOptions | SHOW_PARAMETERS, pramOptions)
    }
  }
}

package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.Tracing.{PsiElementExt, currentParameters, trace}
import org.jetbrains.plugins.scala.extensions.*
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintsResult, ScType}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

/** Tracing of resolve and type inference, the part of [[org.jetbrains.plugins.scala.Tracing]] that needs the PSI and the type system */
@ApiStatus.Internal
object Tracing {
  def resolve(reference: ScReference): Unit = {
    traceInEditor(reference, "Resolve", start = true)
  }

  def resolve(reference: ScReference, result: Array[ScalaResolveResult]): Unit = {
    if (currentParameters.resolve) {
      trace("Resolve: " + reference.asText + " → " + result.map(_.asText).mkString(", "))
    }
    traceInEditor(reference, "Resolve")
  }

  def inference(expression: ScExpression): Unit = {
    traceInEditor(expression, "Inference", start = true)
  }

  def inference(expression: ScExpression, result: ExpressionTypeResult): Unit = {
    if (currentParameters.inference) {
      trace("Inference: " + expression.asText + " → " + result.asText)
    }
    traceInEditor(expression, "Inference")
  }

  /** Whether resolve and type inference are traced in the editor, see [[ElementTracer]] */
  def isHighlightingTracingEnabled: Boolean = Registry.is("scala.highlighting.tracing")

  private def traceInEditor(element: PsiElement, reason: String, start: Boolean = false): Unit =
    if (isHighlightingTracingEnabled) {
      ElementTracer.instance.trace(element, reason, start)
    }

  def equivalence(left: ScType, right: ScType, result: ConstraintsResult): Unit = {
    if (currentParameters.equivalence) {
      trace("Equivalence: (" + left + ", " + right + ") → " + result)
    }
  }

  def conformance(left: ScType, right: ScType, result: ConstraintsResult): Unit = {
    if (currentParameters.conformance) {
      trace("Conformance: (" + left + ", " + right + ") → " + result)
    }
  }

  private implicit class ScalaResolveResultExt(private val that: ScalaResolveResult) {
    def asText: String = that.element match {
      case member: ScMember =>
        val qualifier = Option(member.containingClass).map(_.qualifiedName)
          .orElse(member.getContext.asOptionOf[ScPackaging].map(_.fullPackageName))
        (qualifier ++ Iterable(member.name)).mkString(".")
      case _ => "—"
    }
  }

  private implicit class ExpressionTypeResultExt(private val that: ExpressionTypeResult) {
    def asText: String = that.tr.toOption.map(preciseCanonicalTextOf).getOrElse("—")

    private def preciseCanonicalTextOf(t: ScType): String = {
      ScalaApplicationSettings.getInstance.PRECISE_TEXT = true
      try {
        t.canonicalText.replace("_root_.", "")
      } finally {
        ScalaApplicationSettings.getInstance.PRECISE_TEXT = false
      }
    }
  }
}

/**
 * Shows traced resolve and type inference in the editor.
 * Its implementation, `incremental.Tracing.EditorTracer`, is editor code, so the core knows only this interface.
 */
@ApiStatus.Internal
trait ElementTracer {
  def trace(element: PsiElement, reason: String, start: Boolean): Unit
}

object ElementTracer {
  def instance: ElementTracer = ApplicationManager.getApplication.getService(classOf[ElementTracer])
}

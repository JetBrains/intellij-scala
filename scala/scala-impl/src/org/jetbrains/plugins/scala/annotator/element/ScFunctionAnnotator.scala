package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

object ScFunctionAnnotator extends ElementAnnotator[ScFunction] {
  override def annotate(function: ScFunction, typeAware: Boolean)(implicit holder: ScalaAnnotationHolder): Unit =
    function.getContainingFile match {
      case scFile: ScalaFile if scFile.isScala3OrSource3Enabled =>
        checkTransparentModifier(function)
        checkInlineArguments(function)
      case _ =>
    }

  private def checkTransparentModifier(function: ScFunction)(implicit holder: ScalaAnnotationHolder): Unit = {
    val modifiers = function.getModifierList
    if (modifiers.isTransparent && !modifiers.isInline)
      holder.createErrorAnnotation(function, ScalaBundle.message("transparent.method.must.be.inline"))
  }

  private def checkInlineArguments(function: ScFunction)(implicit holder: ScalaAnnotationHolder): Unit =
    if (!function.getModifierList.isInline && function.parameters.exists(_.isInlineParameter))
      holder.createErrorAnnotation(function, ScalaBundle.message("only.inline.methods.may.have.inline.args"))
}

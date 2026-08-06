package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

object ScFunctionAnnotator extends ElementAnnotator[ScFunction] {
  override def annotate(function: ScFunction, typeAware: Boolean)(implicit holder: ScalaAnnotationHolder): Unit =
    function.getContainingFile match {
      case scFile: ScalaFile if scFile.isScala3File =>
        checkTransparentModifier(function)
        checkInlineArguments(function)
      case _ =>
    }

  private def checkTransparentModifier(function: ScFunction)(implicit holder: ScalaAnnotationHolder): Unit = {
    val modifierList = function.getModifierList
    val transparentModifier = modifierList.findFirstChildByType(ScalaTokenType.TransparentKeyword).orNull
    if (transparentModifier != null && !modifierList.isInline) {
      holder.createErrorAnnotation(transparentModifier, ScalaBundle.message("transparent.method.must.be.inline"))
    }
  }

  private def checkInlineArguments(function: ScFunction)(implicit holder: ScalaAnnotationHolder): Unit = {
    val modifierList = function.getModifierList
    if (!modifierList.isInline) {
      val inlineModifiersInParameters = function.parameters.map(_.getModifierList).filter(_.isInline)
      inlineModifiersInParameters.foreach { inlineToken =>
        holder.createErrorAnnotation(inlineToken, ScalaBundle.message("only.inline.methods.may.have.inline.args"))
      }
    }
  }
}

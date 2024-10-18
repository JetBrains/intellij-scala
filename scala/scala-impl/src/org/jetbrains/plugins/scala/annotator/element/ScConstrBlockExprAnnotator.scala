package org.jetbrains.plugins.scala.annotator.element

import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScSelfInvocation}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

object ScConstrBlockExprAnnotator extends ElementAnnotator[ScFunctionDefinition] with DumbAware {

  override def annotate(fun: ScFunctionDefinition, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    if (fun.isConstructor) {
      val firstElement = firstBodyElement(fun)
      firstElement match {
        case None => // error is added in parser
        case Some(_: ScSelfInvocation) =>
        case Some(element)             =>
          element.getContainingFile match {
            case file: ScalaFile if !file.isCompiled =>
              holder.createErrorAnnotation(element, ScalaBundle.message("constructor.invocation.expected"))
            case _ => //nothing to do in decompiled stuff
          }
      }
    }
  }

  private def firstBodyElement(fun: ScFunctionDefinition): Option[PsiElement] =
    fun.body match {
      case Some(block: ScBlockExpr) =>
        val braceOpt: Option[PsiElement] = block.getLBrace
        braceOpt match {
          case Some(brace) =>
            brace.nextSiblingNotWhitespaceComment
          case _ =>
            block.firstChildNotWhitespaceComment // braceless syntax
        }
      case expr@Some(_) =>
        expr
      case _ =>
        None
    }
}

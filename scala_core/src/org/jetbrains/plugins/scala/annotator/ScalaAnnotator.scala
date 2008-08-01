package org.jetbrains.plugins.scala.annotator

import highlighter.{DefaultHighlighter, AnnotatorHighlighter}
import lang.psi.api.toplevel.{ScEarlyDefinitions, ScTyped}
import lang.psi.api.expr.{ScAnnotationExpr, ScAnnotation, ScNameValuePair, ScReferenceExpression}
import _root_.scala.collection.mutable.HashSet
import lang.psi.api.base.types.ScSimpleTypeElement
import lang.psi.api.toplevel.typedef.ScTrait
import lang.psi.api.base.patterns.ScBindingPattern
import lang.psi.api.base.patterns.ScReferencePattern
import lang.psi.api.statements.ScVariable
import lang.psi.api.toplevel.templates.ScTemplateBody
import lang.psi.api.statements.ScValue
import lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import lang.psi.api.toplevel.typedef.ScObject
import lang.psi.api.toplevel.typedef.ScClass
import lang.lexer.ScalaTokenTypes
import lang.psi.api.statements.params.ScTypeParam
import com.intellij.openapi.util.TextRange
import com.intellij.lang.annotation._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.resolve._
import com.intellij.psi._
import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.annotator.intention._

/** 
* User: Alexander Podkhalyuzin
* Date: 23.06.2008
*/

class ScalaAnnotator extends Annotator {

  def annotate(element: PsiElement, holder: AnnotationHolder) {
    element match {
      case x: ScReferenceExpression if x.qualifier == None => { //todo: temporary case
        x.bind match {
          case Some(_) => AnnotatorHighlighter.highlightReferenceElement(x, holder)
          case None =>
        }
      }
      case x: ScReferenceElement if x.qualifier == None => checkNotQualifiedReferenceElement(x, holder)
      case x: ScReferenceElement => checkQualifiedReferenceElement(x, holder)
      case _ => AnnotatorHighlighter.highlightElement(element, holder)
    }
  }


  private def checkNotQualifiedReferenceElement(refElement: ScReferenceElement, holder: AnnotationHolder) {
    refElement.bind() match {
      case None =>
        //todo: register used imports
        val error = ScalaBundle.message("cannot.resolve", Array[Object](refElement.refName))
        val annotation = holder.createErrorAnnotation(refElement.nameId, error)
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
        registerAddImportFix(refElement, annotation)
      case Some(result) => {
        registerUsedImports(refElement, result)
        AnnotatorHighlighter.highlightReferenceElement(refElement, holder)
      }
    }
  }

  private def checkQualifiedReferenceElement(refElement: ScReferenceElement, holder: AnnotationHolder) {
    refElement.bind() match {
      case None =>
      case _ => AnnotatorHighlighter.highlightReferenceElement(refElement, holder)
    }
  }

  private def registerAddImportFix(refElement: ScReferenceElement, annotation: Annotation) {
    val actions = OuterImportsActionCreator.getOuterImportFixes(refElement, refElement.getProject())
    for (action <- actions) {
      annotation.registerFix(action)
    }
  }

  private def registerUsedImports(refElement: ScReferenceElement, result: ScalaResolveResult) {
  }
}
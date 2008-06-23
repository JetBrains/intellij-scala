package org.jetbrains.plugins.scala.annotator

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
      case x: ScStableCodeReferenceElement if x.qualifier == None => {
        checkNotQualifiedReferenceElement(x, holder)
      }
      case _ =>
    }
  }


  private def checkNotQualifiedReferenceElement(refElement: ScStableCodeReferenceElement, holder: AnnotationHolder) {
    val resolveResult: ScalaResolveResult = refElement.bind() match {
      case Some(x) => x
      case None => null
    }
    //todo: register used imports
    if (resolveResult == null) {
      if (refElement.refName != null) {

        val error = ScalaBundle.message("cannot.resolve", Array[Object](refElement.refName))
        val nameElement = refElement.nameId
        val toHighlight = if (nameElement == null) refElement else nameElement
        val annotation = holder.createErrorAnnotation(toHighlight, error)
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
        registerAddImportFix(refElement,annotation)
      }
    }
  }

  private def registerAddImportFix(refElement: ScStableCodeReferenceElement, annotation: Annotation) {
    val actions = OuterImportsActionCreator.getOuterImportFixes(refElement, refElement.getProject())
    for (action <- actions) {
      annotation.registerFix(action)
    }
  }

  private def registerUsedImports(refElement: ScStableCodeReferenceElement, annotation: Annotation) {
    
  }
}
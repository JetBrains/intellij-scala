package org.jetbrains.plugins.scala.lang.transformation

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.intention.types.UpdateStrategy
import org.jetbrains.plugins.scala.extensions.{&&, FirstChild}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * @author Pavel Fatin
  */
package object annotations {
  def annotationFor(t: ScType, context: PsiElement): ScTypeElement =
    UpdateStrategy.annotationsFor(t, context).head

  def appendTypeAnnotation(e: PsiElement, t: ScType): Unit = {

    val annotation = UpdateStrategy.annotationsFor(t, e).head
    appendTypeAnnotation(e, annotation)
  }

  def appendTypeAnnotation(e: PsiElement, annotation: ScTypeElement): Unit = {
    val whitespace = ScalaPsiElementFactory.createWhitespace(e.getManager)
    val colon = ScalaPsiElementFactory.createColon(e.getManager)

    val parent = e.getParent
    val result = parent.addAfter(annotation, e)
    parent.addAfter(whitespace, e)
    parent.addAfter(colon, e)

    bindTypeElement(result)
  }

  def bindTypeElement(e: PsiElement) {
    e match {
      case (t: ScSimpleTypeElement) && FirstChild(r: ScReferenceElement) =>
        bindTo(r, t.getText)
      case _ => // TODO support compound types
    }
  }
}

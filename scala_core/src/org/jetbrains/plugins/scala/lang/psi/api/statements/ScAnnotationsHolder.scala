package org.jetbrains.plugins.scala.lang.psi.api.statements

import com.intellij.psi.PsiClass
import expr.{ScAnnotations, ScAnnotation}
import types.{ScDesignatorType, ScType}

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.01.2009
 */

trait ScAnnotationsHolder extends ScalaPsiElement {
  def annotations: Seq[ScAnnotation] = if (findChildByClass(classOf[ScAnnotations]) != null)
    findChildByClass(classOf[ScAnnotations]).getAnnotations.toSeq
  else Seq.empty

  def annotationNames: Seq[String] = annotations.map((x: ScAnnotation) => {
    val text: String = x.annotationExpr.constr.typeElement.getText
    text.substring(text.lastIndexOf(".", 0) + 1, text.length)
  })

  def hasAnnotation(clazz: PsiClass): Boolean = {
    annotations.map((x: ScAnnotation) => x.annotationExpr.constr.typeElement.getType.resType match {
      case ScDesignatorType(clazz: PsiClass) => clazz
      case _ => null
    }).find(_.getQualifiedName == clazz.getQualifiedName) match {
      case Some(x) => true
      case _ => false
    }
  }
}
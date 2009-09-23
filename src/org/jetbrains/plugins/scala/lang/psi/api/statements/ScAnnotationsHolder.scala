package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import expr.{ScAnnotations, ScAnnotation}
import types.{ScDesignatorType, ScType}
import java.lang.String
import com.intellij.psi.{PsiAnnotation, PsiAnnotationOwner, PsiClass}

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.01.2009
 */

trait ScAnnotationsHolder extends ScalaPsiElement with PsiAnnotationOwner {
  def annotations: Seq[ScAnnotation] = if (findChildByClass(classOf[ScAnnotations]) != null)
    findChildByClass(classOf[ScAnnotations]).getAnnotations.toSeq
  else Seq.empty

  def annotationNames: Seq[String] = annotations.map((x: ScAnnotation) => {
    val text: String = x.annotationExpr.constr.typeElement.getText
    text.substring(text.lastIndexOf(".", 0) + 1, text.length)
  })

  def hasAnnotation(clazz: PsiClass): Boolean = hasAnnotation(clazz.getQualifiedName) != None

  def hasAnnotation(qualifiedName: String): Option[ScAnnotation] = {
    annotations.find(_.annotationExpr.constr.typeElement.cachedType.resType match {
      case ScDesignatorType(clazz: PsiClass) => clazz.getQualifiedName == qualifiedName
      case _ => false
    })
  }

  def addAnnotation(qualifiedName: String): PsiAnnotation = null //todo:

  def findAnnotation(qualifiedName: String): PsiAnnotation = {
    hasAnnotation(qualifiedName) match {
      case Some(x) => x
      case None => null
    }
  }

  def getApplicableAnnotations: Array[PsiAnnotation] = getAnnotations //todo: understatnd and fix

  def getAnnotations: Array[PsiAnnotation] = annotations.toArray
}
package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import expr.{ScAnnotations, ScAnnotation}
import types.{ScDesignatorType, ScType}
import java.lang.String
import org.jetbrains.plugins.scala.lang.psi.types.Any
import types.result.TypingContext
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.TokenSet
import parser.ScalaElementTypes
import com.intellij.util.ArrayFactory
import com.intellij.psi._

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.01.2009
 */

trait ScAnnotationsHolder extends ScalaPsiElement with PsiAnnotationOwner {
  def annotations: Seq[ScAnnotation] = {
    val stub: StubElement[_ <: PsiElement] = this match {
      case st: StubBasedPsiElement[_] if st.getStub != null => st.getStub
      case file: PsiFileImpl if file.getStub != null => file.getStub
      case _ => null
    }
    if (stub != null) {
      val annots = stub.getChildrenByType(TokenSet.create(ScalaElementTypes.ANNOTATIONS), new ArrayFactory[ScAnnotations] {
        def create(count: Int): Array[ScAnnotations] = new Array[ScAnnotations](count)
      })
      if (annots.length > 0) {
        return annots(0).getAnnotations.toSeq
      } else return Seq.empty
    }
    if (findChildByClassScala(classOf[ScAnnotations]) != null)
      findChildByClassScala(classOf[ScAnnotations]).getAnnotations.toSeq
    else Seq.empty
  }

  def annotationNames: Seq[String] = annotations.map((x: ScAnnotation) => {
    val text: String = x.annotationExpr.constr.typeElement.getText
    text.substring(text.lastIndexOf(".", 0) + 1, text.length)
  })

  def hasAnnotation(clazz: PsiClass): Boolean = hasAnnotation(clazz.getQualifiedName) != None

  def hasAnnotation(qualifiedName: String): Option[ScAnnotation] = {
    annotations.find(_.typeElement.getType(TypingContext.empty).getOrElse(Any) match {
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
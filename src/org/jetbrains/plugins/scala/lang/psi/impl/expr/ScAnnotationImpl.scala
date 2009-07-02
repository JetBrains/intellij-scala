package org.jetbrains.plugins.scala.lang.psi.impl.expr

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import com.intellij.lang.ASTNode
import com.intellij.psi.meta.PsiMetaData

import com.intellij.psi._

import org.jetbrains.plugins.scala.lang.psi.api.expr._
import psi.stubs.ScAnnotationStub

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScAnnotationImpl extends ScalaStubBasedElementImpl[ScAnnotation] with ScAnnotation with PsiAnnotationParameterList{
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScAnnotationStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "Annotation"

  def getMetaData: PsiMetaData = null

  def getAttributes: Array[PsiNameValuePair] = annotationExpr.getAttributes.map(_.asInstanceOf[PsiNameValuePair])

  def getParameterList: PsiAnnotationParameterList = this

  def getQualifiedName: String = ScType.extractClassType(annotationExpr.constr.typeElement.cachedType) match {
    case None => null
    case Some((c: PsiClass, _)) => c.getQualifiedName
  }

  def findDeclaredAttributeValue(attributeName: String): PsiAnnotationMemberValue = null

  def findAttributeValue(attributeName: String): PsiAnnotationMemberValue = null

  def getNameReferenceElement: PsiJavaCodeReferenceElement = null


  def setDeclaredAttributeValue[T <: PsiAnnotationMemberValue](attributeName: String, value: T): T = null.asInstanceOf[T]
}
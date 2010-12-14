package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import com.intellij.lang.ASTNode
import com.intellij.psi.meta.PsiMetaData

import com.intellij.psi._

import org.jetbrains.plugins.scala.lang.psi.api.expr._
import psi.stubs.ScAnnotationStub
import util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.types.Any
import types.result.TypingContext
import api.toplevel.typedef.ScClass
import com.intellij.openapi.util.Comparing

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScAnnotationImpl extends ScalaStubBasedElementImpl[ScAnnotation] with ScAnnotation with PsiAnnotationParameterList{
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScAnnotationStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "Annotation"

  def getMetaData: PsiMetaData = null

  def getAttributes: Array[PsiNameValuePair] = annotationExpr.getAttributes.map(_.asInstanceOf[PsiNameValuePair]).toArray

  def getParameterList: PsiAnnotationParameterList = this

  private def getClazz: Option[PsiClass] =
    ScType.extractClass(annotationExpr.constr.typeElement.getType(TypingContext.empty).getOrElse(Any))

  def getQualifiedName: String = getClazz match {
    case None => null
    case Some(c) => c.getQualifiedName
  }

  def findDeclaredAttributeValue(attributeName: String): PsiAnnotationMemberValue = {
    constructor.args match {
      case Some(args) => args.exprs.map(expr => expr match {
        case ass: ScAssignStmt => ass.getLExpression match {
          case ref: ScReferenceExpression if ref.refName == attributeName => ass.getRExpression match {
            case Some(expr) => (true, expr)
            case _ => (false, expr)
          }
          case _ => (false, expr)
        }
        case _ if attributeName == "value" => (true, expr)
        case _ => (false, expr)
      }).find(p => p._1).getOrElse(false, null)._2
      case None => null
    }
  }

  def findAttributeValue(attributeName: String): PsiAnnotationMemberValue = {
    val value = findDeclaredAttributeValue(attributeName)
    if (value != null) return value

    getClazz match {
      case Some(c) =>
        val methods = c.getMethods
        val iterator = methods.iterator
        while (!iterator.isEmpty) {
          val method = iterator.next
          if (method.isInstanceOf[PsiAnnotationMethod] && Comparing.equal(method.getName, attributeName)) {
            return (method.asInstanceOf[PsiAnnotationMethod]).getDefaultValue
          }
        }
      case _ =>
    }
    return null
  }

  def getNameReferenceElement: PsiJavaCodeReferenceElement = null

  def getOwner: PsiAnnotationOwner = null

  def setDeclaredAttributeValue[T <: PsiAnnotationMemberValue](attributeName: String, value: T): T = null.asInstanceOf[T]
}
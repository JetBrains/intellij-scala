package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Comparing
import com.intellij.psi._
import com.intellij.psi.meta.PsiMetaData
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScAnnotationStub
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

/**
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScAnnotationImpl private (stub: StubElement[ScAnnotation], nodeType: IElementType, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node) with ScAnnotation with PsiAnnotationParameterList{
  def this(node: ASTNode) = {this(null, null, node)}
  def this(stub: ScAnnotationStub) = {this(stub, ScalaElementTypes.ANNOTATION, null)}

  override def toString: String = "Annotation"

  def getMetaData: PsiMetaData = null

  def getAttributes: Array[PsiNameValuePair] = annotationExpr.getAttributes.map(_.asInstanceOf[PsiNameValuePair]).toArray

  def getParameterList: PsiAnnotationParameterList = this

  private def getClazz: Option[PsiClass] =
    annotationExpr.constr.typeElement.getType(TypingContext.empty).getOrAny.extractClass()

  def getQualifiedName: String = getClazz match {
    case None => null
    case Some(c) => c.qualifiedName
  }

  def typeElement: ScTypeElement = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScAnnotationStub].getTypeElement
    }
    annotationExpr.constr.typeElement
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
          val method = iterator.next()
          method match {
            case annotMethod: PsiAnnotationMethod if Comparing.equal(method.name, attributeName) =>
              return annotMethod.getDefaultValue
            case _ =>
          }
        }
      case _ =>
    }
    null
  }

  def getNameReferenceElement: PsiJavaCodeReferenceElement = null

  def getOwner: PsiAnnotationOwner = null

  def setDeclaredAttributeValue[T <: PsiAnnotationMemberValue](attributeName: String, value: T): T = {
    val existing: PsiAnnotationMemberValue = findDeclaredAttributeValue(attributeName)
    if (value == null) {
      if (existing == null) {
        return null.asInstanceOf[T]
      }
      def delete(elem: PsiElement) {
        elem.getParent match {
          case arg: ScArgumentExprList =>
            var prev = elem.getPrevSibling
            while (prev != null && (ScalaPsiUtil.isLineTerminator(prev) || prev.isInstanceOf[PsiWhiteSpace]))
              prev = prev.getPrevSibling
            if (prev != null && prev.getNode.getElementType == ScalaTokenTypes.tCOMMA) {
              elem.delete()
              prev.delete()
            } else {
              var next = elem.getNextSibling
              while (next != null && (ScalaPsiUtil.isLineTerminator(next) || next.isInstanceOf[PsiWhiteSpace]))
                next = next.getNextSibling
              if (next != null && next.getNode.getElementType == ScalaTokenTypes.tCOMMA) {
                elem.delete()
                next.delete()
              } else {
                elem.delete()
              }
            }
          case _ => elem.delete()
        }
      }

      existing.getParent match {
        case args: ScArgumentExprList => delete(existing)
        case other => delete(other)
      }
    }
    else {
      if (existing != null) {
        existing.replace(value)
      }
      else {
        val args: Seq[ScArgumentExprList] = annotationExpr.constr.arguments
        if (args.length == 0) {
          return null.asInstanceOf[T] //todo: ?
        }
        val params: Seq[ScExpression] = args.flatMap(arg => arg.exprs)
        if (params.length == 1 && !params(0).isInstanceOf[ScAssignStmt]) {
          params(0).replace(ScalaPsiElementFactory.
            createExpressionFromText(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME + " = " + params(0).getText,
            params(0).getManager))
        }
        var allowNoName: Boolean = params.length == 0 &&
          (PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(attributeName) || null == attributeName)
        var namePrefix: String = null
        if (allowNoName) {
          namePrefix = ""
        }
        else {
          namePrefix = attributeName + " = "
        }

        args(0).addBefore(
          ScalaPsiElementFactory.createExpressionFromText(namePrefix + value.getText, value.getManager), null)
      }
    }
    findDeclaredAttributeValue(attributeName).asInstanceOf[T]
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitAnnotation(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitAnnotation(this)
      case _ => super.accept(visitor)
    }
  }
}
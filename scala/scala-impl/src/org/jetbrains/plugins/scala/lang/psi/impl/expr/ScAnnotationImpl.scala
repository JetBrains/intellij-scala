package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Comparing
import com.intellij.psi._
import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType.ANNOTATION
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotationExpr}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScAnnotationImpl.ScAnnotationParameterList
import org.jetbrains.plugins.scala.lang.psi.stubs.ScAnnotationStub
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
  * @author Alexander Podkhalyuzin
  *         Date: 07.03.2008
  */
class ScAnnotationImpl private(stub: ScAnnotationStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ANNOTATION, node) with ScAnnotation {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScAnnotationStub) = this(stub, null)

  override def toString: String = "Annotation"

  override def getParameterList: PsiAnnotationParameterList = new ScAnnotationParameterList(annotationExpr)

  private def getClazz: Option[PsiClass] =
    typeElement.`type`().getOrAny.extractClass

  override def getQualifiedName: String = getClazz.map {
    _.qualifiedName
  }.orNull

  override def typeElement: ScTypeElement =
    byPsiOrStub(Option(annotationExpr.constructorInvocation.typeElement))(_.typeElement).orNull

  override def annotationExpr: ScAnnotationExpr =
    byPsiOrStub(Option(findChildByClassScala(classOf[ScAnnotationExpr])))(_.annotationExpr).orNull

  override def findDeclaredAttributeValue(attributeName: String): PsiAnnotationMemberValue = {
    constructorInvocation.args match {
      case Some(args) => args.exprs.map {
        case expr@(ass: ScAssignment) => ass.leftExpression match {
          case ref: ScReferenceExpression if ref.refName == attributeName => ass.rightExpression match {
            case Some(expr) => (true, expr)
            case _ => (false, expr)
          }
          case _ => (false, expr)
        }
        case expr if attributeName == "value" => (true, expr)
        case expr => (false, expr)
      }.find(p => p._1).getOrElse(false, null)._2
      case None => null
    }
  }

  override def findAttributeValue(attributeName: String): PsiAnnotationMemberValue = {
    val value = findDeclaredAttributeValue(attributeName)
    if (value != null) return value

    getClazz match {
      case Some(c) =>
        val methods = c.getMethods
        val iterator = methods.iterator
        while (iterator.nonEmpty) {
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

  override def getNameReferenceElement: PsiJavaCodeReferenceElement = null

  override def getOwner: PsiAnnotationOwner = null

  override def setDeclaredAttributeValue[T <: PsiAnnotationMemberValue](attributeName: String, value: T): T = {
    val existing: PsiAnnotationMemberValue = findDeclaredAttributeValue(attributeName)
    if (value == null) {
      if (existing == null) {
        return null.asInstanceOf[T]
      }
      def delete(elem: PsiElement): Unit = {
        elem.getParent match {
          case _: ScArgumentExprList =>
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
        case _: ScArgumentExprList => delete(existing)
        case other => delete(other)
      }
    }
    else {
      if (existing != null) {
        existing.replace(value)
      }
      else {
        val args = annotationExpr.constructorInvocation.arguments
        if (args.isEmpty) {
          return null.asInstanceOf[T] //todo: ?
        }
        val params = args.flatMap(arg => arg.exprs)
        if (params.length == 1 && !params.head.isInstanceOf[ScAssignment]) {
          params.head.replace(
            createExpressionFromText
              (PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME + " = " + params.head.getText)
              (params.head.getManager)
          )
        }
        val allowNoName: Boolean = params.isEmpty &&
          (PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(attributeName) || null == attributeName)
        var namePrefix: String = null
        if (allowNoName) {
          namePrefix = ""
        }
        else {
          namePrefix = attributeName + " = "
        }

        args.head.addBefore(createExpressionFromText(namePrefix + value.getText)(value.getManager), null)
      }
    }
    findDeclaredAttributeValue(attributeName).asInstanceOf[T]
  }

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitAnnotation(this)
  }

  override def canNavigate: Boolean =
    super[ScalaStubBasedElementImpl].canNavigate

  override def canNavigateToSource: Boolean =
    super[ScalaStubBasedElementImpl].canNavigateToSource

  override def navigate(requestFocus: Boolean): Unit =
    super[ScalaStubBasedElementImpl].navigate(requestFocus)

}

object ScAnnotationImpl {
  private class ScAnnotationParameterList(expr: ScAnnotationExpr)
    extends LightElement(expr.getManager, expr.getLanguage) with PsiAnnotationParameterList {

    override def getAttributes: Array[PsiNameValuePair] = expr.getAttributes.toArray

    override def toString: String = "ScAnnotationParameterList"
  }
}
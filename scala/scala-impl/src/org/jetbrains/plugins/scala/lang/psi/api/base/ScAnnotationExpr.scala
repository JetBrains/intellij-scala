package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi.{PsiAnnotationMemberValue, PsiElement}
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScAssignment, ScExpression, ScNameValuePair, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement.NameId
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

trait ScAnnotationExpr extends ScalaPsiElement {
  def constructorInvocation: ScConstructorInvocation = findChild[ScConstructorInvocation].get

  def getAttributes: Seq[ScNameValuePair] = findArgExprs.map(_.findChildrenByType(ScalaElementType.ASSIGN_STMT)).getOrElse(Seq.empty).map {
    case stmt: ScAssignment => new ScNameValueAssignment(stmt)
  }

  def getAnnotationParameters: Seq[ScExpression] = findArgExprs.map(_.exprs).getOrElse(Seq.empty)

  private def findArgExprs: Option[ScArgumentExprList] = {
    val constrInvocation = findChild[ScConstructorInvocation].get
    if (constrInvocation == null) return None

    constrInvocation.findFirstChildByTypeScala[ScArgumentExprList](ScalaElementType.ARG_EXPRS)
  }

  private class ScNameValueAssignment(assign: ScAssignment) extends ScalaPsiElementImpl(assign.getNode) with ScNameValuePair {
    override def toString: String = "ScNameValueAssignment: " + ifReadAllowed(name)("")

    override def nameId: NameId = assign.leftExpression match {
      case ref: ScReferenceExpression if !ref.isQualified => ref.nameId
      case leftExpression => new NameId.Error(leftExpression)
    }

    override def getName: String = nameId.name.orNull

    override def getValue: PsiAnnotationMemberValue = (assign.rightExpression map {
      case annotationMember: PsiAnnotationMemberValue => annotationMember
      case _ => null
    }).orNull

    override def setValue(newValue: PsiAnnotationMemberValue): PsiAnnotationMemberValue = newValue

    override def getLiteral: Option[ScLiteral] = findChild[ScLiteral]

    override def getLiteralValue: String = {
      getLiteral match {
        case Some(literal) =>
          val value = literal.getValue
          if (value != null) value.toString
          else null
        case _ => null
      }
    }
  }
}

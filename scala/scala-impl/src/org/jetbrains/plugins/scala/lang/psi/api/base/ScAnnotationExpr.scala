package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi.{PsiAnnotationMemberValue, PsiElement}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScAssignment, ScExpression, ScNameValuePair}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScNameValuePairImpl

/**
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScAnnotationExpr extends ScalaPsiElement {
  def constr: ScConstructor = findChildByClassScala(classOf[ScConstructor])

  def getAttributes: Seq[ScNameValuePair] = findArgExprs.map(_.findChildrenByType(ScalaElementType.ASSIGN_STMT)).getOrElse(Seq.empty).map {
    case stmt: ScAssignment => new ScNameValueAssignment(stmt)
  }

  def getAnnotationParameters: Seq[ScExpression] = findArgExprs.map(_.exprs).getOrElse(Seq.empty)

  private def findArgExprs: Option[ScArgumentExprList] = {
    val constr = findChildByClassScala(classOf[ScConstructor])
    if (constr == null) return None

    val args = constr.findFirstChildByType(ScalaElementType.ARG_EXPRS)
    args match {
      case scArgExpr: ScArgumentExprList => Some(scArgExpr)
      case _ => None
    }
  }

  private class ScNameValueAssignment(assign: ScAssignment) extends ScNameValuePairImpl(assign.getNode) {
    override def nameId: PsiElement = assign.getLExpression

    override def getValue: PsiAnnotationMemberValue = (assign.getRExpression map {
      case annotationMember: PsiAnnotationMemberValue => annotationMember
      case _ => null
    }).orNull
  }
}

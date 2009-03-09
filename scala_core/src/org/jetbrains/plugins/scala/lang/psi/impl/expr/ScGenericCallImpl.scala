package org.jetbrains.plugins.scala.lang.psi.impl.expr

import _root_.org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScSubstitutor}
import api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import toplevel.synthetic.ScSyntheticFunction;
import com.intellij.psi._
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import types.Nothing

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScGenericCallImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScGenericCall {
  override def toString: String = "GenericCall"


  override def getType(): ScType = {
    val refType = referencedExpr.getType
    val tp: Seq[String] = referencedExpr match {
      case expr: ScReferenceExpression => expr.resolve match {
        case fun: ScFunction => fun.typeParameters.map(_.name)
        case meth: PsiMethod => meth.getTypeParameters.map(_.getName)
        case synth: ScSyntheticFunction => synth.typeParams.map(_.name)
        case _ => return Nothing //todo:
      }
      case _ => return Nothing //todo:
    }
    val substitutor = ScalaPsiUtil.genericCallSubstitutor(tp, this)
    substitutor.subst(refType)
  }
}
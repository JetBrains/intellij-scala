package org.jetbrains.plugins.scala.lang.psi.impl.expr

import api.statements.{ScFunction, ScFun}
import api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import toplevel.synthetic.ScSyntheticFunction
import toplevel.typedef.TypeDefinitionMembers
import types._;
import com.intellij.psi._
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.expr._

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
      case _ => { //here we must investigate method apply (not update, because can't be generic)
        val parent: PsiElement = getParent
        val params: Seq[ScExpression] = parent match {
          case call: ScMethodCall => call.args.exprs
          case placeholder: ScPlaceholderExpr => Seq.empty //todo:
          case _ => Seq.empty
        }
        //result, we set here to stop loop
        var res: Seq[String] = null
        ScType.extractClassType(refType) match {
          case clazz: PsiClass => {
            val methods = ScalaPsiUtil.getMethodsConforsToMethodCall(ScalaPsiUtil.getApplyMethods(clazz), params)
            if (methods.length == 1) {
              methods(0).method match {
                case fun: ScFunction => fun.typeParameters.map(_.name)
                case meth: PsiMethod => meth.getTypeParameters.map(_.getName)
              }
            } else {
              //todo: according to expected type choose appropriate method
            }
          }
          case _ => return Nothing
        }
        if (res == null) return Nothing
        res
      }
    }
    val substitutor = ScalaPsiUtil.genericCallSubstitutor(tp, this)
    substitutor.subst(refType)
  }
}
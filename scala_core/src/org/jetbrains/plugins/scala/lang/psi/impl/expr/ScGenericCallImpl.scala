package org.jetbrains.plugins.scala.lang.psi.impl.expr

import api.statements.{ScFunction, ScFun}
import api.toplevel.ScTyped
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

    /**
     * Utility method to get generics for apply methods of concrecte class.
     */
    def processClass(clazz: PsiClass, subst: ScSubstitutor): ScType = {
      //ugly method for appling it to methods chooser (to substitute types for every method)
      def createSubst(method: PsiMethod): ScSubstitutor = {
        val tp = method match {
          case fun: ScFunction => fun.typeParameters.map(_.name)
          case meth: PsiMethod => meth.getTypeParameters.map(_.getName)
        }
        subst.followed(ScalaPsiUtil.genericCallSubstitutor(tp, this))
      }
      val parent: PsiElement = getParent
      var isPlaceholder = false
      val params: Seq[ScExpression] = parent match {
        case call: ScMethodCall => call.args.exprs
        case placeholder: ScPlaceholderExpr => {
          isPlaceholder = true
          Seq.empty
        }
        case _ => return null
      }
      val applyMethods = ScalaPsiUtil.getApplyMethods(clazz).filter((sign: PhysicalSignature) => sign.method match {
        case fun: ScFunction => fun.typeParameters.length == this.typeArgs.typeArgs.length
        case meth: PsiMethod => meth.getTypeParameters.length == this.typeArgs.typeArgs.length
      })
      val methods = (if (!isPlaceholder)
        ScalaPsiUtil.getMethodsConforsToMethodCall(applyMethods, params, createSubst(_))
      else
        applyMethods)
      if (methods.length == 1) {
        val method = methods(0).method
        val typez = method match {
          case fun: ScFunction => ScFunctionType(fun.returnType, fun.paramTypes)
          case meth: PsiMethod => ScFunctionType(ScType.create(meth.getReturnType, meth.getProject),
            meth.getParameterList.getParameters.map(param => ScType.create(param.getType, meth.getProject)))
        }
        return createSubst(method).subst(typez)
      } else {
        return Nothing
        //todo: according to expected type choose appropriate method
      }
    }

    // here we get generic names to replace with appropriate substitutor to appropriate types
    val tp: Seq[String] = referencedExpr match {
      case expr: ScReferenceExpression => expr.resolve match {
        case fun: ScFunction => fun.typeParameters.map(_.name)
        case meth: PsiMethod => meth.getTypeParameters.map(_.getName)
        case synth: ScSyntheticFunction => synth.typeParams.map(_.name)
        case clazz: ScObject => {
          return processClass(clazz, ScSubstitutor.empty)
        }
        case clazz: ScClass if clazz.hasModifierProperty("case") => {
          clazz.typeParameters.map(_.name)
        }
        case typed: ScTyped => { //here we must investigate method apply (not update, because can't be generic)
          val scType = typed.calcType
          ScType.extractClassType(scType) match {
            case Some((clazz: PsiClass, subst)) => {
              return processClass(clazz, subst)
            }
            case _ => return Nothing
          }
        }
        case _ => return Nothing //todo: check Java cases (PsiField for example)
      }
      case _ => { //here we must investigate method apply (not update, because can't be generic)
        ScType.extractClassType(refType) match {
          case Some((clazz: PsiClass, subst)) => {
            return processClass(clazz, subst)
          }
          case _ => return Nothing
        }
      }
    }
    val substitutor = ScalaPsiUtil.genericCallSubstitutor(tp, this)
    substitutor.subst(refType)
  }
}
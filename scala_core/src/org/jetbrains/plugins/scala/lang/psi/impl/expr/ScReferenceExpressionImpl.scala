package org.jetbrains.plugins.scala.lang.psi.impl.expr

import api.ScalaFile
import api.toplevel.imports.ScImportExpr
import api.toplevel.typedef.{ScClass, ScTrait}
import api.statements._
import api.base.patterns.ScReferencePattern
import types._
import api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import com.intellij.psi._
import util.PsiTreeUtil

import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.resolve._
import com.intellij.openapi.util._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTyped

/**
 * @author AlexanderPodkhalyuzin
* Date: 06.03.2008
 */

class ScReferenceExpressionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScReferenceExpression {
  override def toString: String = "ReferenceExpression"

  def nameId: PsiElement = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  def bindToElement(element: PsiElement): PsiElement = {
    if (isReferenceTo(element)) return this
    element match {
      case _: ScTrait => this
      case c: ScClass if !c.isCase => this
      case c: PsiClass => {
        val file = getContainingFile.asInstanceOf[ScalaFile]
        if (isReferenceTo(element)) return this
        val qualName = c.getQualifiedName
        if (qualName != null) {
          file.addImportForClass(c)
        }
        this
      }
    }
  }

  def getVariants(): Array[Object] = _resolve(this, new CompletionProcessor(getKinds(true))).map(r => r.getElement)

  import com.intellij.psi.impl.PsiManagerEx

  def multiResolve(incomplete: Boolean) =
    getManager.asInstanceOf[PsiManagerEx].getResolveCache.resolveWithCaching(this, MyResolver, false, incomplete)

  def getKinds(incomplete: Boolean) = {
    getParent match {
      case _: ScReferenceExpression => StdKinds.refExprQualRef
      case _ => StdKinds.refExprLastRef
    }
  }

  import com.intellij.psi.impl.source.resolve.ResolveCache

  object MyResolver extends ResolveCache.PolyVariantResolver[ScReferenceExpressionImpl] {
    def resolve(ref: ScReferenceExpressionImpl, incomplete: Boolean) = {
      val proc = ref.getParent match {
        case call: ScMethodCall =>
          new MethodResolveProcessor(ref, call.args.exprs.map{_.getType}, expectedType)
        case inf: ScInfixExpr if ref == inf.operation => {
          val args = if (ref.rightAssoc) Seq.singleton(inf.lOp.getType) else inf.rOp match {
            case tuple: ScTuple => tuple.exprs.map{_.getType}
            case rOp => Seq.singleton(rOp.getType)
          }
          new MethodResolveProcessor(ref, args, expectedType)
        }
        case postf: ScPostfixExpr if ref == postf.operation =>
          new MethodResolveProcessor(ref, Seq.empty, expectedType)
        case pref: ScPrefixExpr if ref == pref.operation =>
          new MethodResolveProcessor(ref, Seq.empty, expectedType)
        case _ => new RefExprResolveProcessor(getKinds(incomplete), refName)
      }
      _resolve(ref, proc)
    }
  }

  private def _resolve(ref: ScReferenceExpressionImpl, processor: BaseProcessor): Array[ResolveResult] = {
    ref.qualifier match {
      case None => ref.getParent match {
        case inf: ScInfixExpr if ref == inf.operation => {
          val thisOp = if (ref.rightAssoc) inf.rOp else inf.lOp
          processor.processType(thisOp.getType, this)
        }
        case postf: ScPostfixExpr if ref == postf.operation => processor.processType(postf.operand.getType, this)
        case pref: ScPrefixExpr if ref == pref.operation => processor.processType(pref.operand.getType, this)
        case _ => {
          def treeWalkUp(place: PsiElement, lastParent: PsiElement) {
            place match {
              case null =>
              case p => {
                if (!p.processDeclarations(processor,
                  ResolveState.initial(),
                  lastParent, ref)) return
                if (!processor.changedLevel) return
                treeWalkUp(place.getParent, place)
              }
            }
          }
          treeWalkUp(ref, null)
        }
      }
      case Some(q) => processor.processType(q.getType, this)
    }
    processor.candidates
  }

  private def rightAssoc = refName.endsWith(":")

  override def getType(): ScType = {
    bind match {
    //prevent infinite recursion for recursive method invocation
      case Some(ScalaResolveResult(f: ScFunction, s)) if (PsiTreeUtil.getParentOfType(this, classOf[ScFunction]) == f) =>
        new ScFunctionType(s.subst(f.declaredType), f.paramTypes.map{
          s.subst _
        })
      case Some(ScalaResolveResult(fun: ScFun, s)) => new ScFunctionType(s.subst(fun.retType), fun.paramTypes.map{
        s.subst _
      })

      //prevent infinite recursion for recursive pattern reference
      case Some(ScalaResolveResult(refPatt: ScReferencePattern, s)) => {
        def substIfSome(t: Option[ScType]) = t match {
          case Some(t) => s.subst(t)
          case None => Nothing
        }

        refPatt.getParent.getParent match {
          case pd: ScPatternDefinition if (PsiTreeUtil.isAncestor(pd, this, true)) => substIfSome(pd.declaredType)
          case vd: ScVariableDefinition if (PsiTreeUtil.isAncestor(vd, this, true)) => substIfSome(vd.declaredType)
          case _ => s.subst(refPatt.calcType)
        }
      }

      case Some(ScalaResolveResult(typed: ScTyped, s)) => s.subst(typed.calcType)
      case Some(ScalaResolveResult(pack: PsiPackage, _)) => new ScDesignatorType(pack)
      case Some(ScalaResolveResult(clazz: PsiClass, _)) => new ScDesignatorType(clazz)
      case Some(ScalaResolveResult(field: PsiField, s)) => s.subst(ScType.create(field.getType, field.getProject))
      case Some(ScalaResolveResult(method: PsiMethod, s)) => ResolveUtils.methodType(method, s)
      case _ => Nothing
    }
  }
}
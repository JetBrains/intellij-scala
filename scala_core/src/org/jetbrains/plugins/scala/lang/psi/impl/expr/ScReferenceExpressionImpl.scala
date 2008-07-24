package org.jetbrains.plugins.scala.lang.psi.impl.expr

import api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import com.intellij.psi._

import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.resolve._
import com.intellij.openapi.util._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTyped

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScReferenceExpressionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScReferenceExpression {
  override def toString: String = "ReferenceExpression"

  def nameId: PsiElement = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  def bindToElement(element: PsiElement): PsiElement = {
    return this;
    //todo
  }

  def getVariants(): Array[Object] = {
    _resolve(this, new CompletionProcessor(getKinds(true))).map(r => r.getElement)
  }

  import com.intellij.psi.impl.PsiManagerEx

  def multiResolve(incomplete: Boolean) =
    getManager.asInstanceOf[PsiManagerEx].getResolveCache.resolveWithCaching(this, MyResolver, false, incomplete)

  def getKinds(incomplete: Boolean) = {
    if (incomplete) StdKinds.refExprQualRef
    else getParent match {
      case _: ScReferenceExpression => StdKinds.refExprQualRef
      case _ => StdKinds.refExprLastRef
    }
  }

  import com.intellij.psi.impl.source.resolve.ResolveCache

  object MyResolver extends ResolveCache.PolyVariantResolver[ScReferenceExpressionImpl] {
    def resolve(ref: ScReferenceExpressionImpl, incomplete: Boolean) = {
      val proc = ref.getParent match {
        case call: ScMethodCall =>
          new MethodResolveProcessor(refName, call.args.exprs.map{
            _.getType
          }, expectedType)
        case inf : ScInfixExpr if ref == inf.operation => {
          val args = if (ref.rightAssoc) Seq.singleton(inf.lOp.getType) else inf.rOp match {
            case tuple : ScTuple => tuple.exprs.map {_.getType}
            case rOp => Seq.singleton(rOp.getType)
          }
          new MethodResolveProcessor(refName, args, expectedType)
        }
        case _ => new ResolveProcessor(getKinds(incomplete), refName)
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
        case _ => {
          def treeWalkUp(place: PsiElement, lastParent: PsiElement): Unit = {
            place match {
              case null => ()
              case p => {
                if (!p.processDeclarations(processor,
                ResolveState.initial(),
                lastParent, ref)) return ()
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
    //todo return singleton type in the contexts it is needed
    bind match {
      case Some(ScalaResolveResult(typed: ScTyped, s)) => s.subst(typed.calcType)
      case Some(ScalaResolveResult(pack: PsiPackage, _)) => new ScDesignatorType(pack)
      case Some(ScalaResolveResult(clazz: PsiClass, _)) => new ScDesignatorType(clazz)
      case Some(ScalaResolveResult(field: PsiField, s)) => s.subst(ScType.create(field.getType, field.getProject))
      case Some(ScalaResolveResult(method: PsiMethod, s)) => s.subst(ScType.create(method.getReturnType, method.getProject))
      case _ => Nothing
    }
  }
}
package org.jetbrains.plugins.scala.lang.psi.impl.expr

import api.ScalaFile
import api.statements._
import api.base.patterns.ScReferencePattern
import api.toplevel.imports.usages.ImportUsed
import api.toplevel.typedef.{ScClass, ScTypeDefinition, ScTrait}
import com.intellij.util.IncorrectOperationException
import resolve._

import types._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

import com.intellij.lang.ASTNode
import com.intellij.psi._
import util.PsiTreeUtil

import org.jetbrains.plugins.scala.lang.psi.api.expr._
import com.intellij.openapi.util._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTyped
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.{PsiElement, PsiInvalidElementAccessException}
import formatting.settings.ScalaCodeStyleSettings

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
        if (!ResolveUtils.kindMatches(element, getKinds(false)))
          throw new IncorrectOperationException("class does not match expected kind")
        if (refName != c.getName)
          throw new IncorrectOperationException("class does not match expected name")
        val file = getContainingFile.asInstanceOf[ScalaFile]
        if (isReferenceTo(element)) return this
        val qualName = c.getQualifiedName
        if (qualName != null) {
          file.addImportForClass(c)
        }
        this
      }
      case _ => throw new IncorrectOperationException("Cannot bind to anything but class")
    }
  }

  def getVariants(): Array[Object] = _resolve(this, new CompletionProcessor(getKinds(true))).map(r => r.getElement)

  def getSameNameVariants: Array[Object] = _resolve(this, new SameNameCompletionProcessor(getKinds(true), refName)).map(r => r.getElement)

  import com.intellij.psi.impl.PsiManagerEx

  def multiResolve(incomplete: Boolean) =
    getManager.asInstanceOf[PsiManagerEx].getResolveCache.resolveWithCaching(this, MyResolver, true, incomplete)

  def getKinds(incomplete: Boolean) = {
    getParent match {
      case _: ScReferenceExpression => StdKinds.refExprQualRef
      case _ => StdKinds.refExprLastRef
    }
  }

  import com.intellij.psi.impl.source.resolve.ResolveCache

  object MyResolver extends ResolveCache.PolyVariantResolver[ScReferenceExpressionImpl] {
    def resolve(ref: ScReferenceExpressionImpl, incomplete: Boolean) = {
      def proc(e : PsiElement) : ResolveProcessor = e.getContext match {
        case generic : ScGenericCall => proc(generic)
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

      _resolve(ref, proc(ref))
    }
  }

  private def _resolve(ref: ScReferenceExpressionImpl, processor: BaseProcessor): Array[ResolveResult] = {
    def processTypes(e: ScExpression) = {
      processor.processType(getType, e, ResolveState.initial)

      val settings: ScalaCodeStyleSettings = CodeStyleSettingsManager.getSettings(getProject).getCustomSettings(classOf[ScalaCodeStyleSettings])
      if (settings.CHECK_IMPLICITS && processor.candidates.length == 0 && !processor.isInstanceOf[CompletionProcessor]) {
        for (t <- e.getImplicitTypes) {
          processor.processType(t, e, ResolveState.initial.put(ImportUsed.key, e.getImportsForImplicit(t)))
        }
      }
    }

      ref.qualifier match {
      case None => ref.getContext match {
        case inf: ScInfixExpr if ref == inf.operation => {
          val thisOp = if (ref.rightAssoc) inf.rOp else inf.lOp
          processTypes(thisOp)
        }
        case postf: ScPostfixExpr if ref == postf.operation => processTypes(postf.operand)
        case pref: ScPrefixExpr if ref == pref.operation => processTypes(pref.operand)
        case _ => {
          def treeWalkUp(place: PsiElement, lastParent: PsiElement) {
            place match {
              case null =>
              case p => {
                if (!p.processDeclarations(processor,
                  ResolveState.initial(),
                  lastParent, ref)) return
                if (!processor.changedLevel) return
                treeWalkUp(place.getContext, place)
              }
            }
          }
          treeWalkUp(ref, null)
        }
      }
      case Some(superQ : ScSuperReference) => ResolveUtils.processSuperReference(superQ, processor, this)
      case Some(q) => processTypes(q)
    }
    processor.candidates
  }

  private def rightAssoc = refName.endsWith(":")

  override def getType(): ScType = {
    bind match {
    //prevent infinite recursion for recursive method invocation
      case Some(ScalaResolveResult(f: ScFunction, s)) if (PsiTreeUtil.getContextOfType(this, classOf[ScFunction], false) == f) =>
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

        refPatt.getContext().getContext() match {
          case pd: ScPatternDefinition if (PsiTreeUtil.isAncestor(pd, this, true)) => substIfSome(pd.declaredType)
          case vd: ScVariableDefinition if (PsiTreeUtil.isAncestor(vd, this, true)) => substIfSome(vd.declaredType)
          case _ => s.subst(refPatt.calcType)
        }
      }

      case Some(ScalaResolveResult(typed: ScTyped, s)) => s.subst(typed.calcType)
      case Some(ScalaResolveResult(pack: PsiPackage, _)) => ScDesignatorType(pack)
      case Some(ScalaResolveResult(clazz: ScTypeDefinition, s)) if clazz.typeParameters.length != 0 =>
        s.subst(ScParameterizedType(ScDesignatorType(clazz), clazz.typeParameters.map(new ScTypeParameterType(_, s)).toArray))
      case Some(ScalaResolveResult(clazz: PsiClass, s)) if clazz.getTypeParameters.length != 0 =>
        s.subst(ScParameterizedType(ScDesignatorType(clazz), clazz.getTypeParameters.map(new ScTypeParameterType(_, s)).toArray))
      case Some(ScalaResolveResult(clazz: PsiClass, s)) => s.subst(ScDesignatorType(clazz))
      case Some(ScalaResolveResult(field: PsiField, s)) => s.subst(ScType.create(field.getType, field.getProject))
      case Some(ScalaResolveResult(method: PsiMethod, s)) => ResolveUtils.methodType(method, s)
      case _ => Nothing
    }
  }
}

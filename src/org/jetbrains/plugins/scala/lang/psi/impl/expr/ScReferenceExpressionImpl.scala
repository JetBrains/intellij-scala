package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticValue
import api.ScalaFile
import api.statements._
import api.toplevel.imports.usages.ImportUsed
import api.toplevel.typedef.{ScClass, ScTypeDefinition, ScTrait}
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.IncorrectOperationException
import params.ScParameter
import resolve._

import types._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

import com.intellij.lang.ASTNode
import com.intellij.psi._
import impl.PsiManagerEx
import result.{TypeResult, Failure, Success, TypingContext}
import util.PsiTreeUtil

import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import com.intellij.psi.{PsiElement}
import api.base.types.ScTypeElement
import implicits.ScImplicitlyConvertible
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.psi.api.ScalaElementVisitor
import api.base.patterns.{ScBindingPattern, ScReferencePattern}
import types.Compatibility.Expression
import Compatibility.Expression._
import com.intellij.psi.impl.PsiManagerEx
import util.PsiTreeUtil
import com.intellij.psi.impl.source.resolve.ResolveCache
import api.base.ScReferenceElement


/**
 * @author AlexanderPodkhalyuzin
* Date: 06.03.2008
 */

class ScReferenceExpressionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScReferenceExpression {
  case class ContextInfo(arguments: Option[Seq[Expression]], expectedType: () => Option[ScType], isUnderscore: Boolean)

  override def toString: String = "ReferenceExpression"

  def nameId: PsiElement = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  override def accept(visitor: ScalaElementVisitor) = visitor.visitReferenceExpression(this)

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
          org.jetbrains.plugins.scala.annotator.intention.
                ScalaImportClassFix.getImportHolder(ref = this, project = getProject).
                addImportForClass(c, ref = this)
        }
        this
      }
      case _ => throw new IncorrectOperationException("Cannot bind to anything but class: " + element)
    }
  }

  def getVariants: Array[Object] = getVariants(true)

  override def getVariants(implicits: Boolean): Array[Object] = {
    val tp = wrap(qualifier).flatMap (_.getType(TypingContext.empty)).getOrElse(psi.types.Nothing)

    doResolve(this, new CompletionProcessor(getKinds(true), implicits)).map(r => {
      r match {
        case res: ScalaResolveResult => ResolveUtils.getLookupElement(res, tp)
        case _ => r.getElement
      }
    })
  }

  def getSameNameVariants: Array[ResolveResult] = doResolve(this, new CompletionProcessor(getKinds(true), true, Some(refName)))

  def multiResolve(incomplete: Boolean) = {
    //val now = System.currentTimeMillis
    val resolve = getManager.asInstanceOf[PsiManagerEx].getResolveCache.resolveWithCaching(this, MyResolver, true, incomplete)
    /*val spent = System.currentTimeMillis - now
    if (spent > 8000) {
      println("spent: " + spent)
    }*/
    resolve
  }

  def getKinds(incomplete: Boolean) = {
    getContext match {
      case _: ScReferenceExpression => StdKinds.refExprQualRef
      case _ => StdKinds.refExprLastRef
    }
  }

  def isAssignmentOperator = {
    val context = getContext
    (context.isInstanceOf[ScInfixExpr] || context.isInstanceOf[ScMethodCall]) &&
            refName.endsWith("=") && !(refName.startsWith("=") || Seq("!=", "<=", ">=").contains(refName))
  }

  def isUnaryOperator = {
    getContext match {
      case pref: ScPrefixExpr if pref.operation == this => true
      case _ => false
    }
  }


  private object MyResolver extends ResolveCache.PolyVariantResolver[ScReferenceExpressionImpl] {
    private def getContextInfo(ref: ScReferenceExpressionImpl, e: ScExpression): ContextInfo = {
      e.getContext match {
        case generic : ScGenericCall => getContextInfo(ref, generic)
        case call: ScMethodCall => ContextInfo(Some(call.argumentExpressions), () => None, false)
        case section: ScUnderscoreSection => ContextInfo(None, () => section.expectedType, true)
        case inf: ScInfixExpr if ref == inf.operation => {
          ContextInfo(if (ref.rightAssoc) Some(Seq(inf.lOp)) else inf.rOp match {
            case tuple: ScTuple => Some(tuple.exprs)
            case rOp => Some(Seq(rOp))
          }, () => None, false)
        }
        case parents: ScParenthesisedExpr => getContextInfo(ref, parents)
        case postf: ScPostfixExpr if ref == postf.operation => getContextInfo(ref, postf)
        case pref: ScPrefixExpr if ref == pref.operation => getContextInfo(ref, pref)
        case _ => ContextInfo(None, () => e.expectedType, false)
      }
    }

    private def kinds(ref: ScReferenceExpressionImpl, e: ScExpression, incomplete: Boolean): scala.collection.Set[ResolveTargets.Value] = {
      e.getContext match {
        case gen: ScGenericCall => kinds(ref, gen, incomplete)
        case parents: ScParenthesisedExpr => kinds(ref, parents, incomplete)
        case _: ScMethodCall | _: ScUnderscoreSection => StdKinds.methodRef
        case inf: ScInfixExpr if ref == inf.operation => StdKinds.methodRef
        case postf: ScPostfixExpr if ref == postf.operation => StdKinds.methodRef
        case pref: ScPrefixExpr if ref == pref.operation => StdKinds.methodRef
        case _ => getKinds(incomplete)
      }
    }

    private def getTypeArgs(e : ScExpression) : Seq[ScTypeElement] = {
      e.getContext match {
        case generic: ScGenericCall => generic.arguments
        case parents: ScParenthesisedExpr => getTypeArgs(parents)
        case _ => Seq.empty
      }
    }

    def resolve(ref: ScReferenceExpressionImpl, incomplete: Boolean): Array[ResolveResult] = {
      //TODO move this knowledge into MethodResolveProcessor
      val name = if(ref.isUnaryOperator) "unary_" + refName else refName

      val info = getContextInfo(ref, ref)

      val processor = new MethodResolveProcessor(ref, name, info.arguments.toList,
        getTypeArgs(ref), kinds(ref, ref, incomplete), info.expectedType.apply, info.isUnderscore)

      val result = doResolve(ref, processor)

      if (result.isEmpty && ref.isAssignmentOperator) {
        doResolve(ref, new MethodResolveProcessor(ref, refName.init, List(argumentsOf(ref)), Nil))
      } else {
        result
      }
    }
  }

  private def argumentsOf(ref: PsiElement): Seq[Expression] = {
    ref.getContext match {
      case infixExpr: ScInfixExpr => {
        //TODO should rOp really be parsed as Tuple (not as argument list)?
        infixExpr.rOp match {
          case t: ScTuple => t.exprs
          case op => Seq(op)
        }
      }
      case methodCall: ScMethodCall => methodCall.argumentExpressions
    }
  }

  private def doResolve(ref: ScReferenceExpressionImpl, processor: BaseProcessor): Array[ResolveResult] = {
    ref.qualifier match {
      case None => resolveUnqalified(ref, processor)
      case Some(superQ : ScSuperReference) => ResolveUtils.processSuperReference(superQ, processor, this)
      case Some(q) => processTypes(q, processor)
    }
    processor.candidates
  }

  private def resolveUnqalified(ref: ScReferenceExpressionImpl, processor: BaseProcessor) = {
    ref.getContext match {
      case inf: ScInfixExpr if ref == inf.operation => {
        val thisOp = if (ref.rightAssoc) inf.rOp else inf.lOp
        processTypes(thisOp, processor)
      }
      case postf: ScPostfixExpr if ref == postf.operation => processTypes(postf.operand, processor)
      case pref: ScPrefixExpr if ref == pref.operation => processTypes(pref.operand, processor)
      case _ => resolveUnqualifiedExpression(ref, processor)
    }
  }

  private def resolveUnqualifiedExpression(ref: ScReferenceExpressionImpl, processor: BaseProcessor) = {
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
    ref.getContext match {
      case assign: ScAssignStmt if assign.getLExpression == ref &&
              assign.getContext.isInstanceOf[ScArgumentExprList] => processAssignment(assign, ref, processor)
      case _ => //todo: constructors
    }
    treeWalkUp(ref, null)
  }

  private def processAssignment(assign: ScAssignStmt, ref: ScReferenceExpressionImpl, processor: BaseProcessor) {
    assign.getContext match { //trying to resolve naming parameter
      case args: ScArgumentExprList => {
        val exprs = args.exprs
        args.callReference match {
          case Some(callReference) => processCallReference(args, callReference, ref, processor)
          case None =>
        }
      }
    }
  }

  private def processCallReference(args: ScArgumentExprList, callReference: ScReferenceElement,
          ref: ScReferenceExpressionImpl, processor: BaseProcessor) = {
    for (variant <- callReference.multiResolve(false)) {
      variant match {
        case ScalaResolveResult(fun: ScFunction, subst: ScSubstitutor) => {
          fun.getParamByName(ref.refName, args.invocationCount - 1) match {
            case Some(param) => processor.execute(param, ResolveState.initial.put(ScSubstitutor.key, subst))
            case None =>
          }
        }
        case _ =>
      }
    }
  }

  private def processTypes(e: ScExpression, processor: BaseProcessor) {
    ProgressManager.checkCanceled
    e match {
      case ref: ScReferenceExpression if ref.multiResolve(false).length > 1 => {
        for (tp <- ref.multiType) {
          processor.processType(tp, e, ResolveState.initial)
        }
      }
      case _ => {
        val result = e.getType(TypingContext.empty)
        if (result.isDefined) {
          processType(result.get, e, processor)
        }
      }
    }
  }

  private def processType(aType: ScType, e: ScExpression, processor: BaseProcessor) {
    processor.processType(aType, e, ResolveState.initial)

    val candidates = processor.candidates

    if (candidates.length == 0 || (processor.isInstanceOf[CompletionProcessor] &&
            processor.asInstanceOf[CompletionProcessor].collectImplicits)) {
      collectImplicits(e, processor)
    }
  }

  private def collectImplicits(e: ScExpression, processor: BaseProcessor) {
    for (t <- e.getImplicitTypes) {
        ProgressManager.checkCanceled
        val importsUsed = e.getImportsForImplicit(t)
        var state = ResolveState.initial.put(ImportUsed.key, importsUsed)
        e.getClazzForType(t) match {
          case Some(cl: PsiClass) => state = state.put(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY, cl)
          case _ =>
        }
        processor.processType(t, e, state)
      }
  }

  private def rightAssoc = refName.endsWith(":")

  def multiType: Array[ScType] = {
    val buffer = new ArrayBuffer[ScType]
    for (res <- multiResolve(false); if res.isInstanceOf[ScalaResolveResult]; resolve = res.asInstanceOf[ScalaResolveResult]) {
      convertBindToType(Some(resolve)) match {
        case Success(tp: ScType, elem) => buffer += tp
        case _ =>
      }
    }
    return buffer.toArray
  }

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    convertBindToType(bind)
  }

  protected def convertBindToType(bind: Option[ScalaResolveResult]): TypeResult[ScType] = {
    def isMethodCall: Boolean = {
      var parent = getContext
      while (parent != null && parent.isInstanceOf[ScGenericCall]) parent = parent.getContext
      parent match {
        case _: ScUnderscoreSection | _: ScMethodCall => true
        case _ => false
      }
    }
    val inner: ScType = bind match {
    //prevent infinite recursion for recursive method invocation
      case Some(ScalaResolveResult(f: ScFunction, s: ScSubstitutor))
        if (PsiTreeUtil.getContextOfType(this, classOf[ScFunction], false) == f) => {
        val result: Option[ScType] = f.declaredType match {
          case s: Success[ScType] => Some(s.get)
          // this is in case if function has super method => recursion has defined type
          case fail: Failure => f.superMethod match {
            case Some(fun: ScFunction) => fun.returnType match {case s: Success[ScType] => Some(s.get) case _ => None}
            case Some(meth: PsiMethod) => Some(ScType.create(meth.getReturnType, meth.getProject))
            case _ => None
          }
        }
        s.subst(f.polymorphicType(result))
      }
      case Some(ScalaResolveResult(fun: ScFun, s)) => {
        s.subst(fun.polymorphicType)
      }

      //prevent infinite recursion for recursive pattern reference
      case Some(ScalaResolveResult(refPatt: ScReferencePattern, s)) => {
        def substIfSome(t: Option[ScType]) = t match {
          case Some(t) => s.subst(t)
          case None => Nothing
        }

        refPatt.getContext().getContext() match {
          case pd: ScPatternDefinition if (PsiTreeUtil.isAncestor(pd, this, true)) => pd.declaredType match {
            case Some(t) => t
            case None => return Failure("No declared type found", Some(this))
          }
          case vd: ScVariableDefinition if (PsiTreeUtil.isAncestor(vd, this, true)) => vd.declaredType match {
            case Some(t) => t
            case None => return Failure("No declared type found", Some(this))
          }
          case _ => {
            val result = refPatt.getType(TypingContext.empty)
            s.subst(result.getOrElse(return result))
          }
        }
      }
      case Some(ScalaResolveResult(value: ScSyntheticValue, _)) => value.tp
      case Some(ScalaResolveResult(fun: ScFunction, s)) => {
        s.subst(fun.polymorphicType)
      }
      case Some(ScalaResolveResult(param: ScParameter, s)) if param.isRepeatedParameter => {
        val seqClass = JavaPsiFacade.getInstance(getProject).
                findClass("scala.collection.Seq", getResolveScope)
        val result = param.getType(TypingContext.empty)
        val computeType = s.subst(result.getOrElse(return result))
        if (seqClass != null) {
          ScParameterizedType(ScDesignatorType(seqClass), Seq(computeType))
        } else computeType
      }
      case Some(ScalaResolveResult(typed: ScTypedDefinition, s)) => {
        val result = typed.getType(TypingContext.empty)
        s.subst(result.getOrElse(return result))
      }
      case Some(ScalaResolveResult(pack: PsiPackage, _)) => ScDesignatorType(pack)
      case Some(ScalaResolveResult(clazz: ScClass, s)) if clazz.isCase => {
        s.subst(clazz.constructor.
                getOrElse(return Failure("Case Class hasn't primary constructor", Some(this))).polymorphicType)
      }
      case Some(ScalaResolveResult(clazz: ScTypeDefinition, s)) if clazz.typeParameters.length != 0 =>
        s.subst(ScParameterizedType(ScDesignatorType(clazz),
          collection.immutable.Seq(clazz.typeParameters.map(new ScTypeParameterType(_, s)).toSeq: _*)))
      case Some(ScalaResolveResult(clazz: PsiClass, s)) if clazz.getTypeParameters.length != 0 =>
        s.subst(ScParameterizedType(ScDesignatorType(clazz),
          collection.immutable.Seq(clazz.getTypeParameters.map(new ScTypeParameterType(_, s)).toSeq: _*)))
      case Some(ScalaResolveResult(clazz: PsiClass, s)) => s.subst(ScDesignatorType(clazz))
      case Some(ScalaResolveResult(field: PsiField, s)) => s.subst(ScType.create(field.getType, field.getProject))
      case Some(ScalaResolveResult(method: PsiMethod, s)) => {
        ResolveUtils.javaPolymorphicType(method, s)
      }
      case _ => return Failure("Cannot resolve expression", Some(this))
    }
    Success(inner, Some(this))
  }
}

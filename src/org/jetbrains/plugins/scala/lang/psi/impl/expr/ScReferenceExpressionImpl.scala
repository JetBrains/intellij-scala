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

/**
 * @author AlexanderPodkhalyuzin
* Date: 06.03.2008
 */

class ScReferenceExpressionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScReferenceExpression {
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

    _resolve(this, new CompletionProcessor(getKinds(true), implicits)).map(r => {
      r match {
        case res: ScalaResolveResult => ResolveUtils.getLookupElement(res, tp)
        case _ => r.getElement
      }
    })
  }

  def getSameNameVariants: Array[ResolveResult] = _resolve(this, new CompletionProcessor(getKinds(true), true, Some(refName)))

  import com.intellij.psi.impl.PsiManagerEx

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
    getParent match {
      case _: ScReferenceExpression => StdKinds.refExprQualRef
      case _ => StdKinds.refExprLastRef
    }
  }

  import com.intellij.psi.impl.source.resolve.ResolveCache

  object MyResolver extends ResolveCache.PolyVariantResolver[ScReferenceExpressionImpl] {
    def resolve(ref: ScReferenceExpressionImpl, incomplete: Boolean): Array[ResolveResult] = {
      import Compatibility.Expression._
      def defineProcessor(e : ScExpression, typeArgs: Seq[ScTypeElement]) : ResolveProcessor = e.getContext match {
        case generic : ScGenericCall => defineProcessor(generic, generic.arguments)

        case _: ScMethodCall | _ : ScUnderscoreSection =>
          def defProc1(e: ScExpression, argsClauses: List[Seq[ScExpression]]) : MethodResolveProcessor =  e.
                  getContext match {
            case call: ScMethodCall =>
              defProc1(call, argsClauses ::: List(call.argumentExpressions)) //todo rewrite this crap!
            case section: ScUnderscoreSection => new MethodResolveProcessor(ref, ref.refName, argsClauses,
              typeArgs, section.expectedType, section = true)
            case _ => new MethodResolveProcessor(ref, ref.refName, argsClauses, typeArgs, e.expectedType)
          }
          defProc1(e.asInstanceOf[ScExpression], Nil)

        case inf: ScInfixExpr if ref == inf.operation => {
          val args = if (ref.rightAssoc) Seq.singleton(inf.lOp) else inf.rOp match {
            case tuple: ScTuple => tuple.exprs
            case rOp => Seq.singleton(rOp)
          }
          new MethodResolveProcessor(ref, ref.refName, List(args), Nil, inf.expectedType)
        }

        case postf: ScPostfixExpr if ref == postf.operation =>
          new MethodResolveProcessor(ref, ref.refName,  Nil, Nil, postf.expectedType)

        case pref: ScPrefixExpr if ref == pref.operation =>
          new MethodResolveProcessor(ref, "unary_" + ref.refName, Nil, Nil, pref.expectedType)

        case _ => new MethodResolveProcessor(ref, ref.refName, Nil, typeArgs, e.expectedType, getKinds(incomplete), true)
      }

      val res = _resolve(ref, defineProcessor(ref, Nil))
      if (refName.endsWith("=") && refName.length > 1 && res.length == 0) {
        //we should check if it's infix method like +=
        ref.getContext match {
          case inf: ScInfixExpr => {
            inf.lOp match {
              case referenceExpression: ScReferenceExpression => {
                referenceExpression.resolve match {
                  case patt: ScBindingPattern => {
                    ScalaPsiUtil.nameContext(patt) match {
                      case _var: ScVariable => {
                        val args = inf.rOp match {
                          case tuple: ScTuple => tuple.exprs
                          case rOp => Seq.singleton(rOp)
                        }
                        val processor = new MethodResolveProcessor(ref, refName.substring(0, refName.length - 1),
                          List(args), Nil, patt.getType(TypingContext.empty) match {
                            case Success(tp, _) => Some(tp)
                            case _ => None
                          })
                        return _resolve(ref, processor)
                      }
                      case _ => return res
                    }
                  }
                  case _ => 
                }
              }
              case _ => return res
            }
          }
          case _ => return res
        }
      }
      res
    }
  }

  private def _resolve(ref: ScReferenceExpressionImpl, processor: BaseProcessor): Array[ResolveResult] = {
    def processTypes(e: ScExpression) {
      ProgressManager.checkCanceled
      e match {
        case ref: ScReferenceExpression if ref.multiResolve(false).length > 1 => {
          for (tp <- ref.multiType) {
            processor.processType(tp, e, ResolveState.initial)
          }
          return
        }
        case _ =>
      }
      val result = e.getType(TypingContext.empty).getOrElse(return) //do not resolve if Type is unknown
      processor.processType(result, e, ResolveState.initial)
      if (processor.candidates.length == 0 || (processor.isInstanceOf[CompletionProcessor] &&
              processor.asInstanceOf[CompletionProcessor].collectImplicits)) {
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
          ref.getParent match {
            case assign: ScAssignStmt if assign.getLExpression == ref &&
                    assign.getParent.isInstanceOf[ScArgumentExprList] => {
              assign.getParent match { //trying to resolve naming parameter
                case args: ScArgumentExprList => {
                  val exprs = args.exprs
                  val assignName = ref.refName
                  var resultFound = false
                  args.callReference match {
                    case Some(callReference) => {
                      val count = args.invocationCount
                      val variants = callReference.multiResolve(false)
                      for (variant <- variants) {
                        variant match {
                          case ScalaResolveResult(fun: ScFunction, subst: ScSubstitutor) => {
                            fun.getParamByName(assignName, count - 1) match {
                              case Some(param) => {
                                processor.execute(param, ResolveState.initial.put(ScSubstitutor.key, subst))
                                resultFound = true
                              }
                              case None =>
                            }
                          }
                          case _ =>
                        }
                      }
                    }
                    case None =>
                  }
                  if (!resultFound) {
                    //todo: do it for types
                  }
                }
              }
            }
            case _ => //todo: constructors
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
      var parent = getParent
      while (parent != null && parent.isInstanceOf[ScGenericCall]) parent = parent.getParent
      parent match {
        case _: ScUnderscoreSection | _: ScMethodCall => true
        case _ => false
      }
    }
    val inner: ScType = bind match {
    //prevent infinite recursion for recursive method invocation
      case Some(ScalaResolveResult(f: ScFunction, s: ScSubstitutor))
        if (PsiTreeUtil.getContextOfType(this, classOf[ScFunction], false) == f) => {
        val result: TypeResult[ScType] = f.declaredType match {
          case s: Success[ScType] => s
          // this is in case if function has super method => resursion has defined type
          case fail: Failure => f.superMethod match {
            case Some(fun: ScFunction) => fun.returnType
            case Some(meth: PsiMethod) => Success(ScType.create(meth.getReturnType, meth.getProject), None)
            case _ => fail
          }
        }
        if (isMethodCall) new ScFunctionType(s.subst(result.getOrElse(return result)),
          f.paramTypes.map{s.subst _}, getProject)
        else s.subst(result.getOrElse(return result))
      }
      case Some(ScalaResolveResult(fun: ScFun, s)) => {
        if (isMethodCall) new ScFunctionType(s.subst(fun.retType),
          collection.immutable.Seq(fun.paramTypes.map({
            s.subst _
          }).toSeq: _*), getProject)
        else s.subst(fun.retType)
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
        val result = if (isMethodCall) fun.getType(TypingContext.empty)
        else fun.returnType
        s.subst(result.getOrElse(return result))
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
      case Some(ScalaResolveResult(clazz: ScTypeDefinition, s)) if clazz.typeParameters.length != 0 =>
        s.subst(ScParameterizedType(ScDesignatorType(clazz),
          collection.immutable.Seq(clazz.typeParameters.map(new ScTypeParameterType(_, s)).toSeq: _*)))
      case Some(ScalaResolveResult(clazz: PsiClass, s)) if clazz.getTypeParameters.length != 0 =>
        s.subst(ScParameterizedType(ScDesignatorType(clazz),
          collection.immutable.Seq(clazz.getTypeParameters.map(new ScTypeParameterType(_, s)).toSeq: _*)))
      case Some(ScalaResolveResult(clazz: PsiClass, s)) => s.subst(ScDesignatorType(clazz))
      case Some(ScalaResolveResult(field: PsiField, s)) => s.subst(ScType.create(field.getType, field.getProject))
      case Some(ScalaResolveResult(method: PsiMethod, s)) => {
        if (isMethodCall) ResolveUtils.methodType(method, s)
        else s.subst(ScType.create(method.getReturnType, getProject))
      }
      case _ => return Failure("Cannot resolve expression", Some(this))
    }
    Success(inner, Some(this))
  }
}

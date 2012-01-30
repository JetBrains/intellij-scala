package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticValue
import api.statements._
import params.ScParameter
import resolve._
import processor.{MethodResolveProcessor, CompletionProcessor}
import types._
import nonvalue.{TypeParameter, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._
import result.{TypeResult, Failure, Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import api.ScalaElementVisitor
import api.toplevel.typedef.{ScObject, ScClass, ScTypeDefinition, ScTrait}
import api.toplevel.imports.ScImportStmt
import caches.ScalaRecursionManager
import com.intellij.openapi.util.Computable
import api.base.patterns.{ScBindingPattern, ScReferencePattern}
import api.base.ScFieldId
import com.intellij.util.IncorrectOperationException
import annotator.intention.ScalaImportClassFix

/**
 * @author AlexanderPodkhalyuzin
 * Date: 06.03.2008
 */

class ScReferenceExpressionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ResolvableReferenceExpression {
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "ReferenceExpression"

  def nameId: PsiElement = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitReferenceExpression(this)
  }

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
        val qualName = c.getQualifiedName
        if (qualName != null) {
          org.jetbrains.plugins.scala.annotator.intention.
                  ScalaImportClassFix.getImportHolder(ref = this, project = getProject).
                  addImportForClass(c, ref = this)
        }
        this
      }
      case t: ScTypeAlias =>
        throw new IncorrectOperationException("type does not match expected kind")
      case elem: PsiNamedElement =>
        if (refName != elem.getName)
          throw new IncorrectOperationException("named element does not match expected name")
        ScalaPsiUtil.nameContext(elem) match {
          case memb: PsiMember =>
            val containingClass = memb.getContainingClass
            if (containingClass != null && containingClass.getQualifiedName != null) {
              ScalaImportClassFix.getImportHolder(this, getProject).
                addImportForPsiNamedElement(elem, this)
            }
          case _ =>
        }
        this
      case _ => throw new IncorrectOperationException("Cannot bind to anything but class: " + element)
    }
  }

  def getVariants: Array[Object] = getVariants(true, false)

  /**
   * Important! Do not change types of Object values, this can cause errors due to bad architecture.
   */
  override def getVariants(implicits: Boolean, filterNotNamedVariants: Boolean): Array[Object] = {
    val isInImport: Boolean = ScalaPsiUtil.getParentOfType(this, classOf[ScImportStmt]) != null

    doResolve(this, new CompletionProcessor(getKinds(true), implicits)).filter(r => {
      if (filterNotNamedVariants) {
        r match {
          case res: ScalaResolveResult => res.isNamedParameter
          case _ => false
        }
      } else true
    }).flatMap {
      case res: ScalaResolveResult =>
        import org.jetbrains.plugins.scala.lang.psi.types.Nothing
        val qualifier = res.fromType.getOrElse(Nothing)
        ResolveUtils.getLookupElement(res, isInImport = isInImport, qualifierType = qualifier, isInStableCodeReference = false)
      case r => Seq(r.getElement)
    }
  }

  def getSameNameVariants: Array[ResolveResult] = doResolve(this, new CompletionProcessor(getKinds(true), true, Some(refName)))

  def getKinds(incomplete: Boolean, completion: Boolean = false) = {
    getContext match {
      case _ if completion => StdKinds.refExprQualRef // SC-3092
      case _: ScReferenceExpression => StdKinds.refExprQualRef
      case postf: ScPostfixExpr if this == postf.operation => StdKinds.refExprQualRef
      case pref: ScPrefixExpr if this == pref.operation => StdKinds.refExprQualRef
      case inf: ScInfixExpr if this == inf.operation => StdKinds.refExprQualRef
      case _ => StdKinds.refExprLastRef
    }
  } // See SCL-3092

  def multiType: Array[TypeResult[ScType]] = {
    multiResolve(false).filter(_.isInstanceOf[ScalaResolveResult]).
      map(r => convertBindToType(Some(r.asInstanceOf[ScalaResolveResult])))
  }

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    convertBindToType(bind())
  }

  def shapeType = {
    convertBindToType(shapeResolve match {
      case Array(bind: ScalaResolveResult) if bind.isApplicable => Some(bind)
      case _ => None
    })
  }

  def shapeMultiType: Array[TypeResult[ScType]] = {
    shapeResolve.filter(_.isInstanceOf[ScalaResolveResult]).
      map(r => convertBindToType(Some(r.asInstanceOf[ScalaResolveResult])))
  }

  protected def convertBindToType(bind: Option[ScalaResolveResult]): TypeResult[ScType] = {
    /**
     * This method created for the following example:
     * {{{
     *   new HashMap + (1 -> 2)
     * }}}
     * Method + has lower bound, which is second generic parameter of HashMap.
     * In this case new HashMap should create HashMap[Int, Nothing], then we can invoke + method.
     * However we can't use information from not inferred generic. So if such method use bounds on
     * not inferred generics, such bounds should be removed.
     */
    def removeBadBounds(tp: ScType): ScType = {
      tp match {
        case tp@ScTypePolymorphicType(internal, typeParameters) =>
          def hasBadLinks(tp: ScType, ownerPtp: PsiTypeParameter): Option[ScType] = {
            var res: Option[ScType] = Some(tp)
            tp.recursiveUpdate {tp => 
              tp match {
                case t: ScTypeParameterType =>
                  if (typeParameters.find {
                    case TypeParameter(_, _, _, ptp) if ptp == t.param && ptp.getOwner != ownerPtp.getOwner => true
                    case _ => false
                  } != None) res = None
                case _ =>
              }
              (false, tp)
            }
            res
          }
          
          ScTypePolymorphicType(internal, typeParameters.map {
            case t@TypeParameter(name, lowerType, upperType, ptp) =>
              TypeParameter(name, hasBadLinks(lowerType, ptp).getOrElse(Nothing),
                hasBadLinks(upperType, ptp).getOrElse(Any), ptp)
          })
        case _ => tp
      }
    }

    val fromType: Option[ScType] = bind.map(_.fromType).getOrElse(None)
    val inner: ScType = bind match {
      case Some(ScalaResolveResult(fun: ScFun, s)) => {
        s.subst(fun.polymorphicType)
      }

      //prevent infinite recursion for recursive pattern reference
      case Some(r@ScalaResolveResult(refPatt: ScReferencePattern, s)) => {
        ScalaPsiUtil.nameContext(refPatt) match {
          case pd: ScPatternDefinition if (PsiTreeUtil.isContextAncestor(pd, this, true)) => pd.declaredType match {
            case Some(t) => t
            case None => return Failure("No declared type found", Some(this))
          }
          case vd: ScVariableDefinition if (PsiTreeUtil.isContextAncestor(vd, this, true)) => vd.declaredType match {
            case Some(t) => t
            case None => return Failure("No declared type found", Some(this))
          }
          case _ => {
            val stableTypeRequired = {
              val expectedTypeIsStable = expectedType().exists {_.isStable}
              // TODO there are 4 cases in SLS 6.4, this is #2
              expectedTypeIsStable
            }
            if (stableTypeRequired) {
              r.fromType match {
                case Some(fT) => ScProjectionType(fT, refPatt, ScSubstitutor.empty)
                case None => ScType.designator(refPatt)
              }
            } else {
              val result = refPatt.getType(TypingContext.empty)
              result match {
                case Success(tp, _) => s.subst(tp)
                case _ => return result
              }
            }
          }
        }
      }
      case Some(ScalaResolveResult(value: ScSyntheticValue, _)) => value.tp
      case Some(ScalaResolveResult(fun: ScFunction, s)) if fun.isProbablyRecursive =>
        val optionResult: Option[ScType] = {
          fun.definedReturnType match {
            case s: Success[ScType] => Some(s.get)
            case fail: Failure => None
          }
        }
        s.subst(fun.polymorphicType(optionResult))
      case Some(ScalaResolveResult(fun: ScFunction, s)) => s.subst(fun.polymorphicType)
      case Some(ScalaResolveResult(param: ScParameter, s)) if param.isRepeatedParameter => {
        val seqClass = ScalaPsiManager.instance(getProject).getCachedClass("scala.collection.Seq", getResolveScope, ScalaPsiManager.ClassCategory.TYPE)
        val result = param.getType(TypingContext.empty)
        val computeType = s.subst(result match {
          case Success(tp, _) => tp
          case _ => return result
        })
        if (seqClass != null) {
          ScParameterizedType(ScType.designator(seqClass), Seq(computeType))
        } else computeType
      }
      case Some(ScalaResolveResult(obj: ScObject, s)) => {
        def tail = {
          fromType match {
            case Some(tp) => ScProjectionType(tp, obj, s)
            case _ => ScType.designator(obj)
          }
        }
        if (obj.isSyntheticObject) {
          //hack to add Eta expansion for case classes
          expectedType() match {
            case Some(tp) =>
              val expectedFunction = tp match {
                case _: ScFunctionType => true
                case p: ScParameterizedType => p.getFunctionType != None
                case _ => false
              }
              if (expectedFunction) {
                val tp = tail
                val processor =
                  new MethodResolveProcessor(this, "apply", Nil, Nil, Nil)
                processor.processType(tp, this)
                val candidates = processor.candidates
                if (candidates.length != 1) tail
                else convertBindToType(Some(candidates(0))).getOrElse(tail)
              } else tail
            case _ => tail
          }
        } else tail
      }
      case Some(ScalaResolveResult(typed: ScTypedDefinition, s)) => {
        val result = typed.getType(TypingContext.empty)
        s.subst(result match {
          case Success(tp, _) => tp
          case _ => return result
        })
      }
      case Some(ScalaResolveResult(pack: PsiPackage, _)) => ScType.designator(pack)
      case Some(ScalaResolveResult(clazz: ScClass, s)) if clazz.isCase => {
        s.subst(clazz.constructor.
                getOrElse(return Failure("Case Class hasn't primary constructor", Some(this))).polymorphicType)
      }
      case Some(ScalaResolveResult(clazz: ScTypeDefinition, s)) if clazz.typeParameters.length != 0 =>
        s.subst(ScParameterizedType(ScType.designator(clazz),
          collection.immutable.Seq(clazz.typeParameters.map(new ScTypeParameterType(_, s)).toSeq: _*)))
      case Some(ScalaResolveResult(clazz: PsiClass, _)) => new ScDesignatorType(clazz, true) //static Java class
      case Some(ScalaResolveResult(field: PsiField, s)) =>
        s.subst(ScType.create(field.getType, field.getProject, getResolveScope))
      case Some(ScalaResolveResult(method: PsiMethod, s)) =>
        ResolveUtils.javaPolymorphicType(method, s, getResolveScope)
      case _ => return Failure("Cannot resolve expression", Some(this))
    }
    qualifier match {
      case Some(s: ScSuperReference) =>
      case None => //infix, prefix and postfix
        getContext match {
          case sugar: ScSugarCallExpr if sugar.operation == this =>
            sugar.getBaseExpr.getNonValueType(TypingContext.empty) match {
              case Success(ScTypePolymorphicType(_, typeParams), _) =>
                inner match {
                  case ScTypePolymorphicType(internal, typeParams2) =>
                    return Success(removeBadBounds(ScTypePolymorphicType(internal, typeParams ++ typeParams2)), Some(this))
                  case _ =>
                    return Success(ScTypePolymorphicType(inner, typeParams), Some(this))
                }
              case _ =>
            }
          case _ =>
        }
      case Some(qualifier) =>
        qualifier.getNonValueType(TypingContext.empty) match {
          case Success(ScTypePolymorphicType(_, typeParams), _) =>
            inner match {
              case ScTypePolymorphicType(internal, typeParams2) =>
                return Success(removeBadBounds(ScTypePolymorphicType(internal, typeParams ++ typeParams2)), Some(this))
              case _ =>
                return Success(ScTypePolymorphicType(inner, typeParams), Some(this))
            }
          case _ =>
        }
    }
    Success(inner, Some(this))
  }
}

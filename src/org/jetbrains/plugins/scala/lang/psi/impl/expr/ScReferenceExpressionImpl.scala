package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticValue
import api.statements._
import api.toplevel.imports.usages.ImportUsed
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.IncorrectOperationException
import params.ScParameter
import resolve._

import processor.CompletionProcessor
import types._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.impl.PsiManagerEx
import result.{TypeResult, Failure, Success, TypingContext}

import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import com.intellij.psi.{PsiElement}
import api.base.types.ScTypeElement
import implicits.ScImplicitlyConvertible
import collection.mutable.ArrayBuffer
import api.base.patterns.{ScBindingPattern, ScReferencePattern}
import types.Compatibility.Expression
import Compatibility.Expression._
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.impl.source.resolve.ResolveCache
import api.base.ScReferenceElement
import api.{ScalaElementVisitor, ScalaFile}
import api.toplevel.typedef.{ScObject, ScClass, ScTypeDefinition, ScTrait}


/**
 * @author AlexanderPodkhalyuzin
 * Date: 06.03.2008
 */

class ScReferenceExpressionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ResolvableReferenceExpression {
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

  def getVariants: Array[Object] = getVariants(true, false)

  override def getVariants(implicits: Boolean, filterNotNamedVariants: Boolean): Array[Object] = {
    val tp = wrap(qualifier).flatMap(_.getType(TypingContext.empty)).getOrElse(psi.types.Nothing)

    doResolve(this, new CompletionProcessor(getKinds(true), implicits)).filter(r => {
      if (filterNotNamedVariants) {
        r match {
          case res: ScalaResolveResult => res.isNamedParameter
          case _ => false
        }
      } else true
    }).map(r => {
      r match {
        case res: ScalaResolveResult => ResolveUtils.getLookupElement(res, tp)
        case _ => r.getElement
      }
    })
  }

  def getSameNameVariants: Array[ResolveResult] = doResolve(this, new CompletionProcessor(getKinds(true), true, Some(refName)))

  def getKinds(incomplete: Boolean) = {
    getContext match {
      case _: ScReferenceExpression => StdKinds.refExprQualRef
      case _ => StdKinds.refExprLastRef
    }
  }

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

  def shapeType = {
    convertBindToType(shapeResolve match {
      case Array(bind: ScalaResolveResult) if bind.isApplicable => Some(bind)
      case _ => None
    })
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
    val fromType: Option[ScType] = bind.map(_.fromType).getOrElse(None)
    val inner: ScType = bind match {
    //prevent infinite recursion for recursive method invocation
      case Some(ScalaResolveResult(f: ScFunction, s: ScSubstitutor))
        if (PsiTreeUtil.getContextOfType(this, classOf[ScFunction], false) == f) => {
        val result: Option[ScType] = {
          f.definedReturnType match {
            case s: Success[ScType] => Some(s.get)
            case fail: Failure => None
          }
        }
        if (result == None) return Failure("Cannot infer recursive method type", Some(this))
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
            val stableTypeRequired = {
              val expectedTypeIsStable = expectedType.exists {_.isStable}
              // TODO there are 4 cases in SLS 6.4, this is #2
              expectedTypeIsStable
            }
            if (stableTypeRequired) {
              ScDesignatorType(refPatt)
            } else {
              val result = refPatt.getType(TypingContext.empty)
              s.subst(result.getOrElse(return result))
            }
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
      case Some(ScalaResolveResult(obj: ScObject, s)) => {
        val parentClazz = ScalaPsiUtil.getPlaceTd(obj)
        if (parentClazz != null)
          ScProjectionType(ScThisType(parentClazz), obj, ScSubstitutor.empty)
        else ScDesignatorType(obj)
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
      case Some(ScalaResolveResult(clazz: PsiClass, _)) => new ScDesignatorType(clazz, true) //static Java class
      case Some(ScalaResolveResult(field: PsiField, s)) =>
        s.subst(ScType.create(field.getType, field.getProject, getResolveScope))
      case Some(ScalaResolveResult(method: PsiMethod, s)) => {
        ResolveUtils.javaPolymorphicType(method, s, getResolveScope)
      }
      case _ => return Failure("Cannot resolve expression", Some(this))
    }
    Success( /*if (fromType != None) inner.updateThisType(fromType.get) else*/ inner, Some(this))
  }
}

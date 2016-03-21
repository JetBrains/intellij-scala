package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.SafeCheckException
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignStmt, ScExpression, ScNewTemplateDefinition, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScClassParents, ScExtendsBlock}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.{InferUtil, ScalaElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSimpleTypeElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType, TypeParameter}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

import scala.collection.Seq
import scala.collection.immutable.HashMap
import scala.collection.mutable.ArrayBuffer

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScConstructorImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScConstructor {

  def typeElement: ScTypeElement = findNotNullChildByClass(classOf[ScTypeElement])

  override def toString: String = "Constructor"

  def expectedType: Option[ScType] = {
    getContext match {
      case parents: ScClassParents =>
        if (parents.allTypeElements.length != 1) None
        else {
          parents.getContext match {
            case e: ScExtendsBlock =>
              e.getContext match {
                case n: ScNewTemplateDefinition =>
                  n.expectedType()
                case _ => None
              }
            case _ => None
          }
        }
      case _ => None
    }
  }

  def newTemplate = {
    getContext match {
      case parents: ScClassParents =>
        parents.getContext match {
          case e: ScExtendsBlock =>
            e.getContext match {
              case n: ScNewTemplateDefinition =>
                Some(n)
              case _ => None
            }
        }
      case _ => None
    }
  }

  //todo: duplicate ScSimpleTypeElementImpl
  def parameterize(tp: ScType, clazz: PsiClass, subst: ScSubstitutor): ScType = {
    if (clazz.getTypeParameters.isEmpty) {
      tp
    } else {
      ScParameterizedType(tp, clazz.getTypeParameters.map {
        case tp: ScTypeParam => new ScTypeParameterType(tp, subst)
        case ptp => new ScTypeParameterType(ptp, subst)
      })
    }
  }

  def shapeType(i: Int): TypeResult[ScType] = {
    val seq = shapeMultiType(i)
    if (seq.length == 1) seq.head
    else Failure("Can't resolve type", Some(this))
  }

  def shapeMultiType(i: Int): Seq[TypeResult[ScType]] = innerMultiType(i, isShape = true)

  def multiType(i: Int): Seq[TypeResult[ScType]] = innerMultiType(i, isShape = false)

  private def innerMultiType(i: Int, isShape: Boolean): Seq[TypeResult[ScType]] = {
    def FAILURE = Failure("Can't resolve type", Some(this))
    def workWithResolveResult(constr: PsiMethod, r: ScalaResolveResult,
                              subst: ScSubstitutor, s: ScSimpleTypeElement,
                              ref: ScStableCodeReferenceElement): TypeResult[ScType] = {
      val clazz = constr.containingClass
      val tp = r.getActualElement match {
        case ta: ScTypeAliasDefinition => subst.subst(ta.aliasedType.getOrElse(return FAILURE))
        case _ =>
          parameterize(ScSimpleTypeElementImpl.calculateReferenceType(ref, shapesOnly = true).
            getOrElse(return FAILURE), clazz, subst)
      }
      val res = constr match {
        case fun: ScMethodLike =>
          val methodType = fun.nestedMethodType(i, Some(tp)).getOrElse(return FAILURE)
          subst.subst(methodType)
        case method: PsiMethod =>
          if (i > 0) return Failure("Java constructors only have one parameter section", Some(this))
          ResolveUtils.javaMethodType(method, subst, getResolveScope, Some(subst.subst(tp)))
      }
      val typeParameters: Seq[TypeParameter] = r.getActualElement match {
        case tp: ScTypeParametersOwner if tp.typeParameters.nonEmpty =>
          tp.typeParameters.map(new TypeParameter(_))
        case ptp: PsiTypeParameterListOwner if ptp.getTypeParameters.nonEmpty =>
          ptp.getTypeParameters.toSeq.map(new TypeParameter(_))
        case _ => return Success(res, Some(this))
      }
      s.getParent match {
        case p: ScParameterizedTypeElement =>
          val zipped = p.typeArgList.typeArgs.zip(typeParameters)
          val appSubst = new ScSubstitutor(new HashMap[(String, PsiElement), ScType] ++ zipped.map {
            case (arg, typeParam) =>
              ((typeParam.name, ScalaPsiUtil.getPsiElementId(typeParam.ptp)), arg.getType(TypingContext.empty).getOrAny)
          }, Map.empty, None)
          Success(appSubst.subst(res), Some(this))
        case _ =>
          var nonValueType = ScTypePolymorphicType(res, typeParameters)
          expectedType match {
            case Some(expected) =>
              try {
                nonValueType = InferUtil.localTypeInference(nonValueType.internalType,
                  Seq(new Parameter("", None, expected, false, false, false, 0)),
                  Seq(new Expression(InferUtil.undefineSubstitutor(nonValueType.typeParameters).
                    subst(subst.subst(tp).inferValueType))),
                  nonValueType.typeParameters, shouldUndefineParameters = false, filterTypeParams = false)
              } catch {
                case s: SafeCheckException => //ignore
              }
            case _ =>
          }
          Success(nonValueType, Some(this))
      }
    }

    def processSimple(s: ScSimpleTypeElement): Seq[TypeResult[ScType]] = {
      s.reference match {
        case Some(ref) =>
          val buffer = new ArrayBuffer[TypeResult[ScType]]
          val resolve = if (isShape) ref.shapeResolveConstr else ref.resolveAllConstructors
          resolve.foreach {
            case r@ScalaResolveResult(constr: PsiMethod, subst) =>
              buffer += workWithResolveResult(constr, r, subst, s, ref)
            case ScalaResolveResult(clazz: PsiClass, subst) if !clazz.isInstanceOf[ScTemplateDefinition] && clazz.isAnnotationType =>
              val params = clazz.getMethods.flatMap {
                case p: PsiAnnotationMethod =>
                  val paramType = subst.subst(p.getReturnType.toScType(getProject, getResolveScope))
                  Seq(Parameter(p.getName, None, paramType, paramType, p.getDefaultValue != null, isRepeated = false, isByName = false))
                case _ => Seq.empty
              }
              buffer += Success(ScMethodType(ScDesignatorType(clazz), params, isImplicit = false)(getProject, getResolveScope), Some(this))
            case _ =>
          }
          buffer.toSeq
        case _ => Seq(Failure("Hasn't reference", Some(this)))
      }
    }

    simpleTypeElement.toSeq.flatMap(processSimple)
  }

  def reference: Option[ScStableCodeReferenceElement] = {
    simpleTypeElement.flatMap(_.reference)
  }

  def simpleTypeElement: Option[ScSimpleTypeElement] = typeElement match {
    case s: ScSimpleTypeElement => Some(s)
    case p: ScParameterizedTypeElement =>
      p.typeElement match {
        case s: ScSimpleTypeElement => Some(s)
        case _ => None
      }
    case _ => None
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitConstructor(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitConstructor(this)
      case _ => super.accept(visitor)
    }
  }

  @Cached(true, ModCount.getBlockModificationCount, this)
  def matchedParameters: Seq[(ScExpression, Parameter)] = {
    val paramClauses = this.reference.flatMap(r => Option(r.resolve())) match {
      case Some(pc: ScPrimaryConstructor) => pc.parameterList.clauses.map(_.parameters)
      case Some(fun: ScFunction) if fun.isConstructor => fun.parameterList.clauses.map(_.parameters)
      case Some(m: PsiMethod) if m.isConstructor => Seq(m.getParameterList.getParameters.toSeq)
      case _ => Seq.empty
    }
    (for {
      (paramClause, argList) <- paramClauses.zip(arguments)
      (arg, idx) <- argList.exprs.zipWithIndex
    } yield {
      arg match {
        case ScAssignStmt(refToParam: ScReferenceExpression, Some(expr)) =>
          val param = paramClause.find(_.getName == refToParam.refName)
            .orElse(refToParam.resolve().asOptionOf[ScParameter])
          param.map(p => (expr, new Parameter(p))).toSeq
        case expr =>
          val paramIndex = Math.min(idx, paramClause.size - 1)
          paramClause.lift(paramIndex).map(p => (expr, new Parameter(p))).toSeq
      }
    }).flatten
  }
}

package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.SafeCheckException
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScClassParents, ScExtendsBlock}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSimpleTypeElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType, TypeParameter}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}

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
        if (parents.typeElements.length != 1) None
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
    if (clazz.getTypeParameters.length == 0) {
      tp
    } else {
      ScParameterizedType(tp, clazz.getTypeParameters.map {
        case tp: ScTypeParam => ScTypeParameterType(tp, subst)
        case ptp => ScTypeParameterType(ptp, subst)
      })
    }
  }

  def shapeType(i: Int): TypeResult[ScType] = {
    val seq = shapeMultiType(i)
    if (seq.length == 1) seq(0)
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
          val methodType = ScType.nested(fun.methodType(Some(tp)), i).getOrElse(return FAILURE)
          subst.subst(methodType)
        case method: PsiMethod =>
          if (i > 0) return Failure("Java constructors only have one parameter section", Some(this))
          ResolveUtils.javaMethodType(method, subst, getResolveScope, Some(subst.subst(tp)))
      }
      val typeParameters: Seq[TypeParameter] = r.getActualElement match {
        case tp: ScTypeParametersOwner if tp.typeParameters.length > 0 =>
          tp.typeParameters.map(new TypeParameter(_))
        case ptp: PsiTypeParameterListOwner if ptp.getTypeParameters.length > 0 =>
          ptp.getTypeParameters.toSeq.map(new TypeParameter(_))
        case _ => return Success(res, Some(this))
      }
      s.getParent match {
        case p: ScParameterizedTypeElement =>
          val zipped = p.typeArgList.typeArgs.zip(typeParameters)
          val appSubst = new ScSubstitutor(new HashMap[(String, String), ScType] ++ zipped.map {
            case (arg, typeParam) =>
              ((typeParam.name, ScalaPsiUtil.getPsiElementId(typeParam.ptp)), arg.getType(TypingContext.empty).getOrAny)
          }, Map.empty, None)
          Success(appSubst.subst(res), Some(this))
        case _ =>
          var nonValueType = ScTypePolymorphicType(res, typeParameters)
          expectedType match {
            case Some(expected) =>
              try {
                nonValueType = ScalaPsiUtil.localTypeInference(nonValueType.internalType,
                  Seq(new Parameter("", None, expected, false, false, false, 0)),
                  Seq(new Expression(ScalaPsiUtil.undefineSubstitutor(nonValueType.typeParameters).
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
                  val paramType = subst.subst(ScType.create(p.getReturnType, getProject, getResolveScope))
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
}
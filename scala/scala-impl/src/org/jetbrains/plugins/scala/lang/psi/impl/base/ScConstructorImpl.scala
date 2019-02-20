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
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScExpression, ScNewTemplateDefinition, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.{InferUtil, ScalaElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSimpleTypeElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameter, TypeParameterType, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

import scala.collection.Seq
import scala.collection.mutable.ArrayBuffer

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScConstructorImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScConstructor {

  def typeElement: ScTypeElement = findNotNullChildByClass(classOf[ScTypeElement])

  override protected def updateImplicitArguments(): Unit =
    if (explicitImplicitArgList.isEmpty) {
      simpleTypeElement.foreach(_.getNonValueType(withUnnecessaryImplicitsUpdate = true))
    }

  override def toString: String = "Constructor"

  def expectedType: Option[ScType] = getContext match {
    case parents: ScTemplateParents =>
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

  def newTemplate: Option[ScNewTemplateDefinition] = getContext match {
    case parents: ScTemplateParents =>
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

  //todo: duplicate ScSimpleTypeElementImpl
  def parameterize(tp: ScType, clazz: PsiClass, subst: ScSubstitutor): ScType = {
    if (clazz.getTypeParameters.isEmpty) {
      tp
    } else {
      ScParameterizedType(tp, clazz.getTypeParameters.map(TypeParameterType(_)))
    }
  }

  def shapeType(i: Int): TypeResult = {
    val seq = shapeMultiType(i)
    if (seq.length == 1) seq.head
    else Failure("Can't resolve type")
  }

  def shapeMultiType(i: Int): Array[TypeResult] = innerMultiType(i, isShape = true)

  def multiType(i: Int): Array[TypeResult] = innerMultiType(i, isShape = false)

  private def innerMultiType(i: Int, isShape: Boolean): Array[TypeResult] = {
    def FAILURE = Failure("Can't resolve type")
    def workWithResolveResult(constr: PsiMethod, r: ScalaResolveResult,
                              subst: ScSubstitutor, s: ScSimpleTypeElement,
                              ref: ScStableCodeReference): TypeResult = {
      val clazz = constr.containingClass
      val tp = r.getActualElement match {
        case ta: ScTypeAliasDefinition => subst(ta.aliasedType.getOrElse(return FAILURE))
        case _ =>
          parameterize(ScSimpleTypeElementImpl.calculateReferenceType(ref, shapesOnly = true).
            getOrElse(return FAILURE), clazz, subst)
      }
      val res = constr match {
        case fun: ScMethodLike =>
          fun.nestedMethodType(i, Some(tp), subst).getOrElse(return FAILURE)
        case method: PsiMethod =>
          if (i > 0) return Failure("Java constructors only have one parameter section")
          val methodType = method.methodTypeProvider(elementScope).methodType(Some(tp))
          subst(methodType)
      }
      val typeParameters: Seq[TypeParameter] = r.getActualElement match {
        case tp: ScTypeParametersOwner if tp.typeParameters.nonEmpty =>
          tp.typeParameters.map(TypeParameter(_))
        case ptp: PsiTypeParameterListOwner if ptp.getTypeParameters.nonEmpty =>
          ptp.getTypeParameters.toSeq.map(TypeParameter(_))
        case _ => return Right(res)
      }
      s.getParent match {
        case p: ScParameterizedTypeElement =>
          val appSubst = ScSubstitutor.bind(typeParameters, p.typeArgList.typeArgs)(_.calcType)
          Right(appSubst(res))
        case _ =>
          var nonValueType = ScTypePolymorphicType(res, typeParameters)
          expectedType match {
            case Some(expected) =>
              try {
                nonValueType = InferUtil.localTypeInference(nonValueType.internalType,
                  Seq(Parameter(expected, isRepeated = false, index = 0)),
                  Seq(new Expression(ScSubstitutor.bind(nonValueType.typeParameters)(UndefinedType(_)).
                    apply(subst(tp).inferValueType))),
                  nonValueType.typeParameters, shouldUndefineParameters = false, filterTypeParams = false)
              } catch {
                case _: SafeCheckException => //ignore
              }
            case _ if i > 0 =>
              val paramsByClauses = matchedParametersByClauses.toArray.apply(i - 1)
              val mySubst = ScSubstitutor.bind(constr.containingClass.getTypeParameters)(UndefinedType(_))
              val undefParams = paramsByClauses.map(_._2).map(
                param => Parameter(mySubst(param.paramType), param.isRepeated, param.index)
              )

              val extRes = Compatibility.checkConformanceExt(false, undefParams, paramsByClauses.map(_._1), false, false)
              val maybeSubstitutor = extRes.constraints match {
                case ConstraintSystem(substitutor) => Some(substitutor)
                case _ => None
              }
              val result = maybeSubstitutor.fold(nonValueType: ScType) {
                _.apply(nonValueType)
              }
              return Right(result)
            case _ =>
          }
          Right(nonValueType)
      }
    }

    def processSimple(s: ScSimpleTypeElement): Array[TypeResult] = {
      s.reference match {
        case Some(ref) =>
          val buffer = new ArrayBuffer[TypeResult]
          val resolve = if (isShape) ref.shapeResolveConstr else ref.resolveAllConstructors
          resolve.foreach {
            case r@ScalaResolveResult(constr: PsiMethod, subst) =>
              buffer += workWithResolveResult(constr, r, subst, s, ref)
            case ScalaResolveResult(clazz: PsiClass, subst) if !clazz.isInstanceOf[ScTemplateDefinition] && clazz.isAnnotationType =>
              val params = clazz.getMethods.flatMap {
                case p: PsiAnnotationMethod =>
                  val paramType = subst(p.getReturnType.toScType())
                  Seq(Parameter(p.getName, None, paramType, paramType, p.getDefaultValue != null, isRepeated = false, isByName = false))
                case _ => Seq.empty
              }
              buffer += Right(ScMethodType(ScDesignatorType(clazz), params, isImplicit = false))
            case _ =>
          }
          buffer.toArray
        case _ => Array(Failure("Hasn't reference"))
      }
    }

    simpleTypeElement.map(processSimple)
      .getOrElse(Array.empty)
  }

  def reference: Option[ScStableCodeReference] = {
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

  override protected def acceptScala(visitor: ScalaElementVisitor) {
    visitor.visitConstructor(this)
  }

  override def matchedParameters: Seq[(ScExpression, Parameter)] = matchedParametersByClauses.flatten

  @Cached(ModCount.getBlockModificationCount, this)
  def matchedParametersByClauses: Seq[Seq[(ScExpression, Parameter)]] = {
    val paramClauses = this.reference.map(_.resolve()).orNull match {
      case ScalaConstructor(constr) => constr.effectiveParameterClauses.map(_.effectiveParameters)
      case JavaConstructor(constr)  => Seq(constr.parameters)
      case _                        => Seq.empty
    }
    (for {
      (paramClause, argList) <- paramClauses.zip(arguments)
    } yield {
      for ((arg, idx) <- argList.exprs.zipWithIndex) yield
        arg match {
          case ScAssignment(refToParam: ScReferenceExpression, Some(expr)) =>
            val param = paramClause.find(_.getName == refToParam.refName)
              .orElse(refToParam.resolve().asOptionOf[ScParameter])
            param.map(p => (expr, Parameter(p))).toSeq
          case expr =>
            val paramIndex = Math.min(idx, paramClause.size - 1)
            paramClause.lift(paramIndex).map(p => (expr, Parameter(p))).toSeq
        }
    }).map(_.flatten)
  }
}

package org.jetbrains.plugins.scala.lang.psi.impl.base

import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.SafeCheckException
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeArgs, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.{InferUtil, ScalaElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
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

import scala.collection.mutable

class ScConstructorInvocationImpl(node: ASTNode)
  extends ScalaPsiElementImpl(node)
    with ScConstructorInvocation with ConstructorInvocationLikeImpl {

  override def typeElement: ScTypeElement =
    findNotNullChildByClass(classOf[ScTypeElement])

  override def typeArgList: Option[ScTypeArgs] = typeElement match {
    case x: ScParameterizedTypeElement => Some(x.typeArgList)
    case _ => None
  }

  override def args: Option[ScArgumentExprList] =
    findChild[ScArgumentExprList]

  override def arguments: Seq[ScArgumentExprList] =
    findChildren[ScArgumentExprList]

  override protected def updateImplicitArguments(): Unit =
    simpleTypeElement.foreach(_.getNonValueType(withUnnecessaryImplicitsUpdate = true))

  override def toString: String = "ConstructorInvocation"

  override def expectedType: Option[ScType] = getContext match {
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

  override def templateDefinitionContext: Option[ScTemplateDefinition] = getContext match {
    case parents: ScTemplateParents =>
      parents.getContext match {
        case e: ScExtendsBlock =>
          e.getContext match {
            case d: ScTemplateDefinition =>
              Some(d)
            case _ => None
          }
      }
    case _ => None
  }

  override def newTemplate: Option[ScNewTemplateDefinition] =
    templateDefinitionContext.filterByType[ScNewTemplateDefinition]

  //todo: duplicate ScSimpleTypeElementImpl
  private def parameterize(tp: ScType, clazz: PsiClass): ScType =
    if (clazz.getTypeParameters.isEmpty) tp
    else
      ScParameterizedType(tp, clazz.getTypeParameters.map(TypeParameterType(_)).toSeq)

  override def shapeType(i: Int): TypeResult = {
    val seq = shapeMultiType(i)
    if (seq.length == 1) seq.head
    else Failure(ScalaBundle.message("can.t.resolve.type"))
  }

  override def shapeMultiType(i: Int): Array[TypeResult] = innerMultiType(i, isShape = true)

  override def multiType(i: Int): Array[TypeResult] = innerMultiType(i, isShape = false)

  private def innerMultiType(i: Int, isShape: Boolean): Array[TypeResult] = {
    def FAILURE = Failure(ScalaBundle.message("can.t.resolve.type"))

    def workWithResolveResult(
      cons:         PsiMethod,
      srr:          ScalaResolveResult,
      subst:        ScSubstitutor,
      typeElement:  ScSimpleTypeElement,
      ref:          ScStableCodeReference
    ): TypeResult = {
      val clazz = cons.containingClass

      val tp = srr.getActualElement match {
        case ta: ScTypeAliasDefinition => subst(ta.aliasedType.getOrElse(return FAILURE))
        case _ =>
          parameterize(
            ScSimpleTypeElementImpl.calculateReferenceType(ref).getOrElse(return FAILURE),
            clazz)
      }

      val res = cons match {
        case fun: ScMethodLike => fun.nestedMethodType(i, Option(tp), subst).getOrElse(return FAILURE)
        case method: PsiMethod =>
          if (i > 0) return Failure(ScalaBundle.message("java.constructors.only.have.one.parameter.section"))
          val methodType = method.methodTypeProvider(elementScope).methodType(Option(tp))
          subst(methodType)
      }

      val clsTypeParameters = srr.getActualElement match {
        case tp: ScTypeParametersOwner if tp.typeParameters.nonEmpty =>
          tp.typeParameters.map(TypeParameter(_))
        case ptp: PsiTypeParameterListOwner if ptp.getTypeParameters.nonEmpty =>
          ptp.getTypeParameters.toSeq.map(TypeParameter(_))
        case _ => Seq.empty
      }

      val typeParameters = srr.element match {
        case JavaConstructor(cons) => clsTypeParameters ++ cons.getTypeParameters.toSeq.map(TypeParameter(_))
        case _                     => clsTypeParameters
      }

      if (typeParameters.isEmpty) return Right(res)

      typeElement.getParent match {
        case p: ScParameterizedTypeElement =>
          val appSubst = ScSubstitutor.bind(typeParameters, p.typeArgList.typeArgs)(_.calcType)
          Right(appSubst(res))
        case _ =>
          var nonValueType = ScTypePolymorphicType(res, typeParameters)
          expectedType match {
            case Some(expected) =>
              try {
                nonValueType =
                  InferUtil.localTypeInference(
                    nonValueType.internalType,
                    Seq(Parameter(expected, isRepeated = false, index = 0)),
                    Seq(
                      Expression(
                        ScSubstitutor
                          .bind(nonValueType.typeParameters)(UndefinedType(_))
                          .apply(subst(tp).inferValueType)
                      )
                    ),
                    nonValueType.typeParameters,
                    shouldUndefineParameters = false,
                    filterTypeParams         = false
                  )
              } catch {
                case _: SafeCheckException => //ignore
              }
            case _ if i > 0 =>
              val paramsByClauses = matchedParametersByClauses.toArray.apply(i - 1)
              val mySubst         = ScSubstitutor.bind(cons.containingClass.getTypeParameters)(UndefinedType(_))

              val undefParams = paramsByClauses.map(_._2).map(
                param => Parameter(mySubst(param.paramType), param.isRepeated, param.index)
              )

              val extRes =
                Compatibility.checkMethodApplicability(
                  undefParams,
                  paramsByClauses.map(_._1),
                  withImplicits = false,
                  isOverloaded  = false
                )

              val maybeSubstitutor = extRes.constraints match {
                case ConstraintSystem(substitutor) => Some(substitutor)
                case _                             => None
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
          val builder      = mutable.ArrayBuilder.make[TypeResult]
          val resolve      = ref.resolveAllConstructors

          resolve.foreach {
            case r@ScalaResolveResult(constr: PsiMethod, subst) =>
              builder += workWithResolveResult(constr, r, subst, s, ref)
            case ScalaResolveResult(clazz: PsiClass, subst) if !clazz.is[ScTemplateDefinition] && clazz.isAnnotationType =>
              val params = clazz.getMethods.iterator.flatMap {
                case p: PsiAnnotationMethod =>
                  val paramType = subst(p.getReturnType.toScType())
                  Seq(Parameter(p.name, None, paramType, paramType, p.getDefaultValue != null, isRepeated = false, isByName = false))
                case _ => Seq.empty
              }
              builder += Right(ScMethodType(ScDesignatorType(clazz), params.toSeq, isImplicit = false))
            case _ =>
          }
          builder.result()
        case _ => Array(Failure(ScalaBundle.message("has.no.reference")))
      }
    }

    simpleTypeElement.map(processSimple)
      .getOrElse(Array.empty)
  }

  override def reference: Option[ScStableCodeReference] = {
    simpleTypeElement.flatMap(_.reference)
  }

  override def simpleTypeElement: Option[ScSimpleTypeElement] = typeElement match {
    case s: ScSimpleTypeElement => Some(s)
    case p: ScParameterizedTypeElement =>
      p.typeElement match {
        case s: ScSimpleTypeElement => Some(s)
        case _ => None
      }
    case _ => None
  }

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitConstructorInvocation(this)
  }

  override protected def resolveConstructor(): PsiElement = this.reference.map(_.resolve()).orNull
}

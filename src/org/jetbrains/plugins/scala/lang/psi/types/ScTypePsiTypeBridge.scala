package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaEvaluatorBuilderUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypingContext}
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.annotation.tailrec
import scala.collection.immutable.HashSet

object ScTypePsiTypeBridge extends api.ScTypePsiTypeBridge {
  override implicit lazy val typeSystem = ScalaTypeSystem

  override def toScType(psiType: PsiType,
                        visitedRawTypes: HashSet[PsiClass],
                        paramTopLevel: Boolean,
                        treatJavaObjectAsAny: Boolean): ScType = {
    def bound(boundType: PsiType, paramTopLevel: Boolean) = boundType.toScType(visitedRawTypes, paramTopLevel = paramTopLevel)

    def lower(wildcardType: PsiWildcardType, paramTopLevel: Boolean) =
      if (wildcardType.isSuper) bound(wildcardType.getSuperBound, paramTopLevel = paramTopLevel) else Nothing

    def upper(wildcardType: PsiWildcardType, paramTopLevel: Boolean) =
      if (wildcardType.isExtends) bound(wildcardType.getExtendsBound, paramTopLevel = paramTopLevel) else Any

    psiType match {
      case classType: PsiClassType =>
        val result = classType.resolveGenerics
        result.getElement match {
          case psiTypeParameter: PsiTypeParameter => TypeParameterType(psiTypeParameter, None)
          case clazz if clazz != null && clazz.qualifiedName == "java.lang.Object" =>
            if (paramTopLevel && treatJavaObjectAsAny) Any
            else AnyRef
          case c if c != null =>
            val clazz = c match {
              case o: ScObject => ScalaPsiUtil.getCompanionModule(o).getOrElse(o)
              case _ => c
            }
            if (classType.isRaw && visitedRawTypes.contains(clazz)) return Any
            val tps = clazz.getTypeParameters
            def constructTypeForClass(clazz: PsiClass, withTypeParameters: Boolean = false): ScType = {
              clazz match {
                case wrapper: PsiClassWrapper => return constructTypeForClass(wrapper.definition)
                case _ =>
              }
              val containingClass: PsiClass = clazz.containingClass
              val res =
                if (containingClass == null) ScDesignatorType(clazz)
                else {
                  ScProjectionType(constructTypeForClass(containingClass,
                    withTypeParameters = !clazz.hasModifierProperty("static")), clazz, superReference = false)
                }
              if (withTypeParameters) {
                tps map {
                  TypeParameterType(_)
                } match {
                  case Array() => res
                  case parameters => ScParameterizedType(res, parameters)
                }
              } else res
            }
            val des = constructTypeForClass(clazz)
            val substitutor = result.getSubstitutor
            tps match {
              case Array() => des
              case _ if classType.isRaw =>
                var index = 0
                ScParameterizedType(des, tps.map { tp =>
                  val upper = (tp.getExtendsListTypes ++ tp.getImplementsListTypes) map {
                    _.toScType(visitedRawTypes + clazz)
                  } match {
                    case Array() => Any
                    case Array(head) => head
                    case components => ScCompoundType(components)
                  }

                  ScExistentialArgument(s"_$$${index += 1; index}", Nil, Nothing, upper)
                }).unpackedType
              case _ =>
                var index = 0
                ScParameterizedType(des, tps.map { tp =>
                  def convertInnerJavaWildcardToExistentialType(wild: PsiWildcardType) =
                    ScExistentialArgument(s"_$$${index += 1; index}", Nil, lower(wild, paramTopLevel = true), upper(wild, paramTopLevel = true))

                  substitutor.substitute(tp) match {
                    case wild: PsiWildcardType => convertInnerJavaWildcardToExistentialType(wild)
                    case capture: PsiCapturedWildcardType => convertInnerJavaWildcardToExistentialType(capture.getWildcard)
                    case psiTypeParameter if psiTypeParameter != null => psiTypeParameter.toScType(visitedRawTypes)
                    case _ => TypeParameterType(tp, None)
                  }
                }).unpackedType
            }
          case _ => Nothing
        }
      case wild: PsiWildcardType => ScExistentialType.simpleExistential("_$1", Nil,
        lower(wild, paramTopLevel = false),
        upper(wild, paramTopLevel = false))
      case capture: PsiCapturedWildcardType =>
        toScType(capture.getWildcard, visitedRawTypes, paramTopLevel, treatJavaObjectAsAny)
      case null => Any
      case d: PsiDisjunctionType => Any
      case p: PsiIntersectionType =>
        ScCompoundType(p.getConjuncts.map {
          toScType(_, visitedRawTypes, paramTopLevel, treatJavaObjectAsAny)
        })
      case _ => super.toScType(psiType, visitedRawTypes, paramTopLevel, treatJavaObjectAsAny)
    }
  }

  override def toPsiType(`type`: ScType,
                         project: Project,
                         scope: GlobalSearchScope,
                         noPrimitives: Boolean,
                         skolemToWildcard: Boolean): PsiType = {
    implicit val typeSystem = project.typeSystem

    def isValueType(cl: ScClass): Boolean = cl.superTypes.contains(AnyVal) && cl.parameters.length == 1

    def outerClassHasTypeParameters(proj: ScProjectionType): Boolean = {
      extractClass(proj.projected) match {
        case Some(outer) => outer.hasTypeParameters
        case _ => false
      }
    }

    val t = `type`.removeAliasDefinitions()
    if (t.isInstanceOf[NonValueType]) return toPsiType(t.inferValueType, project, scope)
    def javaObject = createJavaObject(project, scope)
    t match {
      case ScCompoundType(Seq(typez, _*), _, _) => toPsiType(typez, project, scope)
      case ScDesignatorType(c: ScTypeDefinition) if StdType.QualNameToType.contains(c.qualifiedName) =>
        toPsiType(StdType.QualNameToType.get(c.qualifiedName).get, project, scope, noPrimitives, skolemToWildcard)
      case ScDesignatorType(valType: ScClass) if isValueType(valType) =>
        valType.parameters.head.getRealParameterType(TypingContext.empty) match {
          case Success(tp, _) if !(noPrimitives && ScalaEvaluatorBuilderUtil.isPrimitiveScType(tp)) =>
            toPsiType(tp, project, scope, noPrimitives, skolemToWildcard)
          case _ => createType(valType, project)
        }
      case ScDesignatorType(c: PsiClass) => createType(c, project)
      case ParameterizedType(ScDesignatorType(c: PsiClass), args) =>
        if (c.qualifiedName == "scala.Array" && args.length == 1)
          new PsiArrayType(toPsiType(args.head, project, scope))
        else {
          val subst = args.zip(c.getTypeParameters).foldLeft(PsiSubstitutor.EMPTY) {
            case (s, (targ, tp)) => s.put(tp, toPsiType(targ, project, scope, noPrimitives = true, skolemToWildcard = true))
          }
          JavaPsiFacade.getInstance(project).getElementFactory.createType(c, subst)
        }
      case ParameterizedType(proj@ScProjectionType(pr, element, _), args) => proj.actualElement match {
        case c: PsiClass =>
          if (c.qualifiedName == "scala.Array" && args.length == 1) new PsiArrayType(toPsiType(args.head, project, scope))
          else {
            val subst = args.zip(c.getTypeParameters).foldLeft(PsiSubstitutor.EMPTY) {
              case (s, (targ, tp)) => s.put(tp, toPsiType(targ, project, scope, skolemToWildcard = true))
            }
            createType(c, project, subst, raw = outerClassHasTypeParameters(proj))
          }
        case a: ScTypeAliasDefinition =>
          a.aliasedType(TypingContext.empty) match {
            case Success(c: ScParameterizedType, _) =>
              toPsiType(ScParameterizedType(c.designator, args), project, scope, noPrimitives)
            case _ => javaObject
          }
        case _ => javaObject
      }
      case ParameterizedType(TypeParameterType(_, _, _, typeParameter), _) => EmptySubstitutor.getInstance().substitute(typeParameter)
      case proj@ScProjectionType(_, _, _) => proj.actualElement match {
        case clazz: PsiClass =>
          clazz match {
            case syn: ScSyntheticClass => toPsiType(syn.t, project, scope)
            case _ => createType(clazz, project, raw = outerClassHasTypeParameters(proj))
          }
        case elem: ScTypeAliasDefinition =>
          elem.aliasedType(TypingContext.empty) match {
            case Success(typez, _) => toPsiType(typez, project, scope, noPrimitives)
            case Failure(_, _) => javaObject
          }
        case _ => javaObject
      }
      case ScThisType(clazz) => createType(clazz, project)
      case TypeParameterType(_, _, _, typeParameter) => EmptySubstitutor.getInstance().substitute(typeParameter)
      case ex: ScExistentialType => toPsiType(ex.quantified, project, scope, noPrimitives)
      case argument: ScExistentialArgument =>
        val upper = argument.upper
        if (upper.equiv(Any)) {
          val lower = argument.lower
          if (lower.equiv(Nothing)) PsiWildcardType.createUnbounded(PsiManager.getInstance(project))
          else {
            val sup: PsiType = toPsiType(lower, project, scope)
            if (sup.isInstanceOf[PsiWildcardType]) javaObject
            else PsiWildcardType.createSuper(PsiManager.getInstance(project), sup)
          }
        } else {
          val psi = toPsiType(upper, project, scope)
          if (psi.isInstanceOf[PsiWildcardType]) javaObject
          else PsiWildcardType.createExtends(PsiManager.getInstance(project), psi)
        }
      case _ => super.toPsiType(`type`, project, scope, noPrimitives, skolemToWildcard)
    }
  }

  @tailrec
  override def extractClass(`type`: ScType, project: Project): Option[PsiClass] = `type` match {
    case p@ParameterizedType(designator, _) => extractClass(designator, project) //performance improvement
    case _ => super.extractClass(`type`, project)
  }

  override def extractClassType(`type`: ScType,
                                project: Project,
                                visitedAlias: HashSet[ScTypeAlias]): Option[(PsiClass, ScSubstitutor)] =
    `type` match {
      case ScExistentialType(quantified, _) =>
        quantified.extractClassType(project, visitedAlias)
      case _ => super.extractClassType(`type`, project, visitedAlias)
    }
}


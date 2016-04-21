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
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypingContext}
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.annotation.tailrec
import scala.collection.immutable.HashSet

object ScTypePsiTypeBridge extends api.ScTypePsiTypeBridge {
  override implicit lazy val typeSystem = ScalaTypeSystem

  override def toScType(psiType: PsiType,
                        project: Project,
                        scope: GlobalSearchScope,
                        visitedRawTypes: HashSet[PsiClass],
                        paramTopLevel: Boolean,
                        treatJavaObjectAsAny: Boolean): ScType = {
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
                val typeParameters: Array[PsiTypeParameter] = clazz.getTypeParameters
                if (typeParameters.length > 0) {
                  ScParameterizedType(res, typeParameters.map(TypeParameterType(_)))
                } else res
              } else res
            }
            val des = constructTypeForClass(clazz)
            val substitutor = result.getSubstitutor
            tps match {
              case Array() => des
              case _ if classType.isRaw =>
                var index = 0
                ScParameterizedType(des, tps.map({ tp => {
                  val arrayOfTypes: Array[PsiClassType] = tp.getExtendsListTypes ++ tp.getImplementsListTypes
                  ScExistentialArgument(s"_$$${index += 1; index}", Nil, Nothing,
                    arrayOfTypes.length match {
                      case 0 => Any
                      case 1 => toScType(arrayOfTypes.apply(0), project, scope, visitedRawTypes + clazz)
                      case _ => ScCompoundType(arrayOfTypes.map(toScType(_, project, scope, visitedRawTypes + clazz)),
                        Map.empty, Map.empty)
                    })
                }
                })).unpackedType
              case _ =>
                var index = 0
                ScParameterizedType(des, tps.map
                  (tp => {
                    val psiType = substitutor.substitute(tp)
                    psiType match {
                      case wild: PsiWildcardType => ScExistentialArgument(s"_$$${index += 1; index}", Nil,
                        if (wild.isSuper) toScType(wild.getSuperBound, project, scope, visitedRawTypes) else Nothing,
                        if (wild.isExtends) toScType(wild.getExtendsBound, project, scope, visitedRawTypes) else Any)
                      case capture: PsiCapturedWildcardType =>
                        val wild = capture.getWildcard
                        ScExistentialArgument(s"_$$${index += 1; index}", Nil,
                          if (wild.isSuper) toScType(capture.getLowerBound, project, scope, visitedRawTypes) else Nothing,
                          if (wild.isExtends) toScType(capture.getUpperBound, project, scope, visitedRawTypes) else Any)
                      case _ if psiType != null => toScType(psiType, project, scope, visitedRawTypes)
                      case _ => TypeParameterType(tp, None)
                    }
                  }).toSeq).unpackedType
            }
          case _ => Nothing
        }
      case wild: PsiWildcardType => ScExistentialType.simpleExistential("_$1", Nil,
        if (wild.isSuper) toScType(wild.getSuperBound, project, scope, visitedRawTypes) else Nothing,
        if (wild.isExtends) toScType(wild.getExtendsBound, project, scope, visitedRawTypes) else Any)
      case capture: PsiCapturedWildcardType =>
        val wild = capture.getWildcard
        ScExistentialType.simpleExistential("_$1", Nil,
          if (wild.isSuper) toScType(capture.getLowerBound, project, scope, visitedRawTypes) else Nothing,
          if (wild.isExtends) toScType(capture.getUpperBound, project, scope, visitedRawTypes) else Any)
      case null => Any
      case d: PsiDisjunctionType => Any
      case p: PsiIntersectionType =>
        ScCompoundType(p.getConjuncts.map(toScType(_, project, scope, visitedRawTypes, paramTopLevel, treatJavaObjectAsAny)),
          Map.empty, Map.empty)
      case _ => super.toScType(psiType, project, scope, visitedRawTypes, paramTopLevel, treatJavaObjectAsAny)
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
  override def extractClass(`type`: ScType, project: Project) = `type` match {
    case p@ParameterizedType(designator, _) => extractClass(designator, project) //performance improvement
    case _ => super.extractClass(`type`, project)
  }

  override def extractClassType(`type`: ScType,
                                project: Project,
                                visitedAlias: HashSet[ScTypeAlias]): Option[(PsiClass, ScSubstitutor)] =
    `type` match {
      case ScThisType(clazz) => Some(clazz, new ScSubstitutor(`type`))
      case ScDesignatorType(clazz: PsiClass) => Some(clazz, ScSubstitutor.empty)
      case ScDesignatorType(ta: ScTypeAliasDefinition) =>
        if (visitedAlias.contains(ta)) return None
        val result = ta.aliasedType(TypingContext.empty)
        if (result.isEmpty) return None
        extractClassType(result.get, project, visitedAlias + ta)
      case proj@ScProjectionType(p, elem, _) => proj.actualElement match {
        case c: PsiClass => Some((c, proj.actualSubst))
        case t: ScTypeAliasDefinition =>
          if (visitedAlias.contains(t)) return None
          val result = t.aliasedType(TypingContext.empty)
          if (result.isEmpty) return None
          extractClassType(proj.actualSubst.subst(result.get), project, visitedAlias + t)
        case _ => None
      }
      case ScExistentialType(quantified, _) => extractClassType(quantified, project, visitedAlias)
      case p@ParameterizedType(t1, _) =>
        extractClassType(t1, project, visitedAlias) match {
          case Some((c, s)) => Some((c, s.followed(p.substitutor)))
          case None => None
        }
      case _ => super.extractClassType(`type`, project, visitedAlias)
    }
}


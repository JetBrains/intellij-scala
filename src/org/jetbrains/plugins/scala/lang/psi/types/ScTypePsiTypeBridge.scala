package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaEvaluatorBuilderUtil
import org.jetbrains.plugins.scala.extensions.{PsiWildcardTypeExt, _}
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
                        treatJavaObjectAsAny: Boolean)
                       (implicit visitedRawTypes: HashSet[PsiClass],
                        paramTopLevel: Boolean): ScType = {
    psiType match {
      case classType: PsiClassType =>
        val result = classType.resolveGenerics
        result.getElement match {
          case null => Nothing
          case psiTypeParameter: PsiTypeParameter => TypeParameterType(psiTypeParameter, None)
          case clazz if clazz.qualifiedName == "java.lang.Object" =>
            if (paramTopLevel && treatJavaObjectAsAny) Any
            else AnyRef
          case c =>
            val clazz = c match {
              case o: ScObject => ScalaPsiUtil.getCompanionModule(o).getOrElse(o)
              case _ => c
            }
            if (classType.isRaw && visitedRawTypes.contains(clazz)) return Any

            val substitutor = result.getSubstitutor

            def mapper(tp: PsiTypeParameter, index: Int): ScType = {
              def upper = (tp.getExtendsListTypes ++ tp.getImplementsListTypes).toSeq map { jtp =>
                substitutor.substitute(jtp).toScType(visitedRawTypes + clazz)
              } match {
                case Seq() => None
                case Seq(head) => Some(head)
                case components => Some(ScCompoundType(components))
              }

              if (classType.isRaw) {
                createParameter(None, upper, index)
              } else {
                def convertTypeParameter(typeParameter: PsiType): ScType = typeParameter match {
                  case wildcardType: PsiWildcardType => createParameter(wildcardType, index, upper)(visitedRawTypes, paramTopLevel = true)
                  case wildcardType: PsiCapturedWildcardType => convertTypeParameter(wildcardType.getWildcard)
                  case _ => typeParameter.toScType(visitedRawTypes)
                }

                Option(substitutor.substitute(tp))
                  .map(convertTypeParameter)
                  .getOrElse(TypeParameterType(tp, None))
              }
            }

            val designator = constructTypeForClass(clazz)
            clazz.getTypeParameters.toSeq match {
              case Seq() => designator
              case typeParameters =>
                ScParameterizedType(designator, typeParameters.zipWithIndex map {
                  case (tp, index) => mapper(tp, index)
                }).unpackedType
            }
        }
      case wild: PsiWildcardType =>
        val parameter = createParameter(wild)(visitedRawTypes, paramTopLevel = false)
        ScExistentialType(parameter, List(parameter))
      case _: PsiDisjunctionType => Any
      case _ => super.toScType(psiType, treatJavaObjectAsAny)
    }
  }

  private def createParameter(maybeLower: Option[ScType], maybeUpper: Option[ScType], index: Int): ScExistentialArgument =
    ScExistentialArgument(s"_$$${index + 1}", Nil,
      maybeLower.getOrElse(Nothing), maybeUpper.getOrElse(Any))

  private def createParameter(wildcardType: PsiWildcardType, index: Int = 0, maybeUpper: => Option[ScType] = None)
                             (implicit visitedRawTypes: HashSet[PsiClass],
                              paramTopLevel: Boolean): ScExistentialArgument =
    createParameter(wildcardType.lower, wildcardType.upper.orElse(maybeUpper), index)

  private def constructTypeForClass(clazz: PsiClass, withTypeParameters: Boolean = false): ScType = clazz match {
    case wrapper: PsiClassWrapper => constructTypeForClass(wrapper.definition)
    case _ =>
      val designator = Option(clazz.containingClass) map {
        constructTypeForClass(_, withTypeParameters = !clazz.hasModifierProperty("static"))
      } map {
        ScProjectionType(_, clazz, superReference = false)
      } getOrElse ScDesignatorType(clazz)

      if (withTypeParameters) {
        clazz.getTypeParameters.toSeq map {
          TypeParameterType(_)
        } match {
          case Seq() => designator
          case parameters => ScParameterizedType(designator, parameters)
        }
      } else designator
  }

  override def toPsiType(`type`: ScType,
                         project: Project,
                         scope: GlobalSearchScope,
                         noPrimitives: Boolean,
                         skolemToWildcard: Boolean): PsiType = {
    implicit val typeSystem = project.typeSystem

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
        toPsiType(StdType.QualNameToType(c.qualifiedName), project, scope, noPrimitives, skolemToWildcard)
      case ScDesignatorType(valClass: ScClass) if ValueClassType.isValueClass(valClass) =>
        valClass.parameters.head.getRealParameterType(TypingContext.empty) match {
          case Success(tp, _) if !(noPrimitives && ScalaEvaluatorBuilderUtil.isPrimitiveScType(tp)) =>
            toPsiType(tp, project, scope, noPrimitives, skolemToWildcard)
          case _ => createType(valClass, project)
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
      case ParameterizedType(proj@ScProjectionType(_, _, _), args) => proj.actualElement match {
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
    case ParameterizedType(designator, _) => extractClass(designator, project) //performance improvement
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


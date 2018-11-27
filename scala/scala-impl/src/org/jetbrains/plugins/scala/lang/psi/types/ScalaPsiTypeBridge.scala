package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.psi._
import com.intellij.psi.impl.PsiSubstitutorImpl
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

import scala.collection.JavaConverters._

trait ScalaPsiTypeBridge extends api.PsiTypeBridge {
  typeSystem: api.TypeSystem =>

  override def toScType(psiType: PsiType,
                        treatJavaObjectAsAny: Boolean)
                       (implicit visitedRawTypes: Set[PsiClass],
                        paramTopLevel: Boolean): ScType = psiType match {
    case classType: PsiClassType =>
      val result = classType.resolveGenerics
      result.getElement match {
        case null => Nothing
        case psiTypeParameter: PsiTypeParameter => TypeParameterType(psiTypeParameter)
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

          def upper(tp: PsiTypeParameter) = {
            def mapper = substitutor.substitute(_: PsiType).toScType(visitedRawTypes + clazz)

            tp.getExtendsListTypes ++ tp.getImplementsListTypes match {
              case Array() => None
              case Array(head) => Some(mapper(head))
              case components => Some(ScCompoundType(components.map(mapper)))
            }
          }

          def convertTypeParameter(typeParameter: PsiType, tp: PsiTypeParameter, index: Int): ScType = typeParameter match {
            case wildcardType: PsiWildcardType =>
              val (maybeLower, maybeUpper) = bounds(wildcardType, paramTopLevel = true)

              createParameter(
                maybeLower,
                maybeUpper.orElse(if (visitedRawTypes(clazz)) None else upper(tp)),
                index
              )
            case wildcardType: PsiCapturedWildcardType =>
              convertTypeParameter(wildcardType.getWildcard, tp, index)
            case _ =>
              typeParameter.toScType(visitedRawTypes)
          }

          val scSubst = substitutor match {
            case impl: PsiSubstitutorImpl =>
              val entries = impl.getSubstitutionMap.entrySet().asScala.toSeq
              val psiParams = entries.map(_.getKey)
              val scTypes = entries.map(e => e.getValue.toScType(visitedRawTypes))
              ScSubstitutor.bind(psiParams, scTypes)
            case _ => ScSubstitutor.empty
          }

          val designator = constructTypeForClass(clazz, scSubst)
          clazz.getTypeParameters match {
            case Array() => designator
            case typeParameters =>
              val typeArgs = typeParameters.zipWithIndex.map {
                case (tp, index) if classType.isRaw =>
                  createParameter(None, upper(tp), index)
                case (tp, index) =>
                  substitutor.substitute(tp) match {
                    case null => TypeParameterType(tp)
                    case substituted => convertTypeParameter(substituted, tp, index)
                  }
              }
              ScParameterizedType(designator, typeArgs).unpackedType
          }
      }
    case wildcardType: PsiWildcardType =>
      val (maybeLower, maybeUpper) = bounds(wildcardType, paramTopLevel = false)
      ScExistentialType(createParameter(maybeLower, maybeUpper))
    case _: PsiDisjunctionType => Any
    case _ => super.toScType(psiType, treatJavaObjectAsAny)
  }

  private def createParameter(maybeLower: Option[ScType],
                              maybeUpper: Option[ScType],
                              index: Int = 0) = ScExistentialArgument(
    s"_$$${index + 1}",
    Nil,
    maybeLower.getOrElse(Nothing),
    maybeUpper.getOrElse(Any)
  )

  private def bounds(wildcardType: PsiWildcardType, paramTopLevel: Boolean)
                    (implicit visitedRawTypes: Set[PsiClass]): (Option[ScType], Option[ScType]) = {
    def bound(collector: PartialFunction[PsiWildcardType, PsiType]) =
      Some(wildcardType)
        .collect(collector)
        .map(_.toScType(visitedRawTypes, paramTopLevel = paramTopLevel))

    val maybeLower = bound {
      case t if t.isSuper => t.getSuperBound
    }

    val maybeUpper = bound {
      case t if t.isExtends => t.getExtendsBound
    }

    (maybeLower, maybeUpper)
  }

  private def constructTypeForClass(clazz: PsiClass, subst: ScSubstitutor, withTypeParameters: Boolean = false): ScType = clazz match {
    case PsiClassWrapper(definition) => constructTypeForClass(definition, subst)
    case _ =>
      val designator = Option(clazz.containingClass) map {
        constructTypeForClass(_, subst, withTypeParameters = !clazz.hasModifierProperty("static"))
      } map {
        ScProjectionType(_, clazz)
      } getOrElse ScDesignatorType(clazz)

      subst(if (withTypeParameters) {
        clazz.getTypeParameters.toSeq map {
          TypeParameterType(_)
        } match {
          case Seq() => designator
          case parameters => ScParameterizedType(designator, parameters)
        }
      } else designator)
  }

  override def toPsiType(`type`: ScType, noPrimitives: Boolean): PsiType = toPsiTypeInner(`type`, noPrimitives)

  private def toPsiTypeInner(`type`: ScType,
                             noPrimitives: Boolean = false,
                             visitedAliases: Set[ScTypeAlias] = Set.empty)
                             (implicit visitedExistentialArgs: Set[ScExistentialArgument] = Set.empty): PsiType = {

    def outerClassHasTypeParameters(proj: ScProjectionType): Boolean = {
      proj.projected.extractClass match {
        case Some(outer) => outer.hasTypeParameters
        case _ => false
      }
    }

    val t = `type`.removeAliasDefinitions()
    if (t.isInstanceOf[NonValueType]) return toPsiTypeInner(t.inferValueType)

    def javaObject = createJavaObject
    val qualNameToType = projectContext.stdTypes.QualNameToType

    t match {
      case ScCompoundType(Seq(typez, _*), _, _) => toPsiTypeInner(typez)
      case ScDesignatorType(c: ScTypeDefinition) if qualNameToType.contains(c.qualifiedName) =>
        toPsiTypeInner(qualNameToType(c.qualifiedName), noPrimitives)
      case ScDesignatorType(valClass: ScClass) if ValueClassType.isValueClass(valClass) =>
        valClass.parameters.head.getRealParameterType match {
          case Right(tp) if !(noPrimitives && tp.isPrimitive) =>
            toPsiTypeInner(tp, noPrimitives)
          case _ => createType(valClass)
        }
      case ScDesignatorType(c: PsiClass) => createType(c)
      case arrayType(arg) => new PsiArrayType(toPsiTypeInner(arg))
      case ParameterizedType(ScDesignatorType(c: PsiClass), args) =>
        val subst = args.zip(c.getTypeParameters).foldLeft(PsiSubstitutor.EMPTY) {
          case (s, (targ, tp)) => s.put(tp, toPsiTypeInner(targ, noPrimitives = true))
        }
        createType(c, subst)
      case ParameterizedType(proj@ScProjectionType(_, _), args) => proj.actualElement match {
        case c: PsiClass =>
          if (c.qualifiedName == "scala.Array" && args.length == 1) new PsiArrayType(toPsiTypeInner(args.head))
          else {
            val subst = args.zip(c.getTypeParameters).foldLeft(PsiSubstitutor.EMPTY) {
              case (s, (targ, tp)) => s.put(tp, toPsiTypeInner(targ))
            }
            createType(c, subst, raw = outerClassHasTypeParameters(proj))
          }
        case typeAlias: ScTypeAlias if !visitedAliases.contains(typeAlias.physical) =>
          typeAlias.upperBound match {
            case Right(c: ScParameterizedType) =>
              toPsiTypeInner(ScParameterizedType(c.designator, args), noPrimitives, visitedAliases + typeAlias.physical)
            case _ => javaObject
          }
        case _ => javaObject
      }
      case ParameterizedType(TypeParameterType.ofPsi(typeParameter), _) => psiTypeOf(typeParameter)
      case proj@ScProjectionType(_, _) => proj.actualElement match {
        case clazz: PsiClass =>
          clazz match {
            case syn: ScSyntheticClass => toPsiTypeInner(syn.stdType)
            case _ => createType(clazz, raw = outerClassHasTypeParameters(proj))
          }
        case typeAlias: ScTypeAlias if !visitedAliases.contains(typeAlias.physical) =>
          typeAlias.upperBound.toOption
            .map(toPsiTypeInner(_, noPrimitives, visitedAliases + typeAlias.physical))
            .getOrElse(javaObject)
        case _ => javaObject
      }
      case ScThisType(clazz) => createType(clazz)
      case TypeParameterType.ofPsi(typeParameter) => psiTypeOf(typeParameter)
      case ex: ScExistentialType => toPsiTypeInner(ex.quantified, noPrimitives)
      case argument: ScExistentialArgument =>
        val upper = argument.upper
        val manager: PsiManager = projectContext
        if (upper.equiv(Any)) {
          val lower = argument.lower
          if (lower.equiv(Nothing)) PsiWildcardType.createUnbounded(manager)
          else {
            val sup: PsiType = toPsiTypeInner(lower)(visitedExistentialArgs + argument)
            if (sup.isInstanceOf[PsiWildcardType]) javaObject
            else PsiWildcardType.createSuper(manager, sup)
          }
        } else {
          val psi = toPsiTypeInner(upper)(visitedExistentialArgs + argument)
          if (psi.isInstanceOf[PsiWildcardType]) javaObject
          else PsiWildcardType.createExtends(manager, psi)
        }

      case std: StdType => stdToPsiType(std, noPrimitives)
      case _ => javaObject
    }
  }

  private def psiTypeOf(typeParameter: PsiTypeParameter): PsiType =
    EmptySubstitutor.getInstance().substitute(typeParameter)

}


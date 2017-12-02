package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.psi._
import com.intellij.psi.impl.PsiSubstitutorImpl
import org.jetbrains.plugins.scala.extensions.{PsiWildcardTypeExt, _}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType

import scala.collection.JavaConverters._

trait ScalaPsiTypeBridge extends api.PsiTypeBridge {
  typeSystem: api.TypeSystem =>

  override def toScType(psiType: PsiType,
                        treatJavaObjectAsAny: Boolean)
                       (implicit visitedRawTypes: Set[PsiClass],
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
                  case wildcardType: PsiWildcardType if !visitedRawTypes.contains(clazz) =>
                    createParameter(wildcardType, index, upper)(visitedRawTypes, paramTopLevel = true)
                  case wildcardType: PsiWildcardType => createParameter(wildcardType, index)(visitedRawTypes, paramTopLevel = true)
                  case wildcardType: PsiCapturedWildcardType => convertTypeParameter(wildcardType.getWildcard)
                  case _ => typeParameter.toScType(visitedRawTypes)
                }

                Option(substitutor.substitute(tp))
                  .map(convertTypeParameter)
                  .getOrElse(TypeParameterType(tp, None))
              }
            }

            val scSubst = substitutor match {
              case impl: PsiSubstitutorImpl =>
                import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
                ScSubstitutor(impl.getSubstitutionMap.asScala.toMap.filter{case (key,_) => key.isInstanceOf[PsiTypeParameter]}.map {
                  case (key: PsiTypeParameter, value) => (key.nameAndId, value.toScType(visitedRawTypes))
                })
              case _ => ScSubstitutor.empty
            }

            val designator = constructTypeForClass(clazz, scSubst)
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
                             (implicit visitedRawTypes: Set[PsiClass],
                              paramTopLevel: Boolean): ScExistentialArgument =
    createParameter(wildcardType.lower, wildcardType.upper.orElse(maybeUpper), index)

  private def constructTypeForClass(clazz: PsiClass, subst: ScSubstitutor, withTypeParameters: Boolean = false): ScType = clazz match {
    case PsiClassWrapper(definition) => constructTypeForClass(definition, subst)
    case _ =>
      val designator = Option(clazz.containingClass) map {
        constructTypeForClass(_, subst, withTypeParameters = !clazz.hasModifierProperty("static"))
      } map {
        ScProjectionType(_, clazz, superReference = false)
      } getOrElse ScDesignatorType(clazz)

      subst.subst(if (withTypeParameters) {
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
                             visitedAliases: Set[ScTypeAliasDefinition] = Set.empty): PsiType = {

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
      case ParameterizedType(proj@ScProjectionType(_, _, _), args) => proj.actualElement match {
        case c: PsiClass =>
          if (c.qualifiedName == "scala.Array" && args.length == 1) new PsiArrayType(toPsiTypeInner(args.head))
          else {
            val subst = args.zip(c.getTypeParameters).foldLeft(PsiSubstitutor.EMPTY) {
              case (s, (targ, tp)) => s.put(tp, toPsiTypeInner(targ))
            }
            createType(c, subst, raw = outerClassHasTypeParameters(proj))
          }
        case a: ScTypeAliasDefinition if !visitedAliases.contains(a) =>
          a.aliasedType match {
            case Right(c: ScParameterizedType) =>
              toPsiTypeInner(ScParameterizedType(c.designator, args), noPrimitives, visitedAliases + a)
            case _ => javaObject
          }
        case _ => javaObject
      }
      case ParameterizedType(TypeParameterType(_, _, _, typeParameter), _) => EmptySubstitutor.getInstance().substitute(typeParameter)
      case proj@ScProjectionType(_, _, _) => proj.actualElement match {
        case clazz: PsiClass =>
          clazz match {
            case syn: ScSyntheticClass => toPsiTypeInner(syn.stdType)
            case _ => createType(clazz, raw = outerClassHasTypeParameters(proj))
          }
        case elem: ScTypeAliasDefinition if !visitedAliases.contains(elem) =>
          elem.aliasedType.toOption
            .map(toPsiTypeInner(_, noPrimitives, visitedAliases + elem))
            .getOrElse(javaObject)
        case _ => javaObject
      }
      case ScThisType(clazz) => createType(clazz)
      case TypeParameterType(_, _, _, typeParameter) => EmptySubstitutor.getInstance().substitute(typeParameter)
      case ex: ScExistentialType => toPsiTypeInner(ex.quantified, noPrimitives)
      case argument: ScExistentialArgument =>
        val upper = argument.upper
        val manager: PsiManager = projectContext
        if (upper.equiv(Any)) {
          val lower = argument.lower
          if (lower.equiv(Nothing)) PsiWildcardType.createUnbounded(manager)
          else {
            val sup: PsiType = toPsiTypeInner(lower)
            if (sup.isInstanceOf[PsiWildcardType]) javaObject
            else PsiWildcardType.createSuper(manager, sup)
          }
        } else {
          val psi = toPsiTypeInner(upper)
          if (psi.isInstanceOf[PsiWildcardType]) javaObject
          else PsiWildcardType.createExtends(manager, psi)
        }

      case std: StdType => stdToPsiType(std, noPrimitives)
      case _ => javaObject
    }
  }
}


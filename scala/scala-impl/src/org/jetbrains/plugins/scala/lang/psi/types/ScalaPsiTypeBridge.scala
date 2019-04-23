package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.psi.PsiModifier.STATIC
import com.intellij.psi._
import gnu.trove.THashMap
import org.jetbrains.plugins.scala.extensions._
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

  override protected def toScTypeInner(psiType: PsiType,
                                       paramTopLevel: Boolean,
                                       treatJavaObjectAsAny: Boolean)
                                      (implicit rawExistentialArguments: Option[RawExistentialArgs]): ScType = psiType match {
    case classType: PsiClassType =>
      val result = classType.resolveGenerics

      if (PsiClassType.isRaw(result))
        return convertJavaRawType(result, paramTopLevel, treatJavaObjectAsAny)(rawExistentialArguments)

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

          val substitutor = result.getSubstitutor

          constructTypeForClass(clazz, paramTopLevel) { (typeParam, idx) =>
            substitutor.substitute(typeParam) match {
              case null                              => TypeParameterType(typeParam)
              case wildcardType: PsiWildcardType     => existentialArg(s"_$$${idx + 1}", wildcardType, paramTopLevel = true)
              case captured: PsiCapturedWildcardType => existentialArg(s"_$$${idx + 1}", captured.getWildcard, paramTopLevel = true)
              case substed                           => substed.toScType(paramTopLevel)
            }
          }
      }
    case wildcardType: PsiWildcardType =>
      existentialArg("_$1", wildcardType, paramTopLevel = false)
    case _: PsiDisjunctionType => Any
    case _ => super.toScTypeInner(psiType, paramTopLevel, treatJavaObjectAsAny)(rawExistentialArguments)
  }

  private def convertJavaRawType(classResolveResult: PsiClassType.ClassResolveResult,
                                 paramTopLevel: Boolean,
                                 treatJavaObjectAsAny: Boolean)
                                (rawExistentialArgs: Option[RawExistentialArgs]): ScType = {

    val map: RawExistentialArgs =
      rawExistentialArgs.getOrElse(new THashMap())

    val substitutor = classResolveResult.getSubstitutor
    val clazz = classResolveResult.getElement

    def upperForRaw(tp: PsiTypeParameter): ScType = {
      def convertBound(bound: PsiType) = {
        val substedBound = substitutor.substitute(bound)
        toScTypeInner(substedBound, paramTopLevel)(Some(map))
      }

      tp.getExtendsListTypes match {
        case Array() => api.Any
        case Array(head) => convertBound(head)
        case components => ScCompoundType(components.map(convertBound))
      }
    }

    val quantified = constructTypeForClass(clazz, paramTopLevel) { (tp, idx) =>
      rawTypeParamExistentialArg(tp, () => upperForRaw(tp), map)
    }

    if (rawExistentialArgs.isEmpty) { //it is a first invocation for this raw type
      map.values().asScala.foreach(_.initialize())

      //preventing memory leak, this map is captured by existential arguments
      map.clear()
    }

    quantified
  }

  //existential argument for a type parameter of a raw class type
  //it may have recursive reference to itself in it's upper bound, so we should not compute it right away
  private def rawTypeParamExistentialArg(typeParameter: PsiTypeParameter,
                                         upperBound: () => ScType,
                                         map: RawExistentialArgs): ScExistentialArgument = {
    map.computeIfAbsent(typeParameter, _ => ScExistentialArgument.deferred(typeParameter.getName, Seq.empty, () => Nothing, upperBound))
  }

  private def existentialArg(name: String, wildcardType: PsiWildcardType, paramTopLevel: Boolean): ScExistentialArgument = {
    def scType(bound: PsiType): ScType = bound.toScType(paramTopLevel)

    val lower =
      if (wildcardType.isSuper) scType(wildcardType.getSuperBound)
      else api.Nothing

    val upper =
      if (wildcardType.isExtends) scType(wildcardType.getExtendsBound)
      else api.Any

    ScExistentialArgument(name, Seq.empty, lower, upper)
  }

  private def constructTypeForClass(clazz: PsiClass, paramTopLevel: Boolean, withTypeParameters: Boolean = true)
                                   (typeArgFun: (PsiTypeParameter, Int) => ScType): ScType = {

    clazz match {
      case PsiClassWrapper(definition) => constructTypeForClass(definition, paramTopLevel)(typeArgFun)
      case _ =>
        val designator = clazz.containingClass match {
          case null   => ScDesignatorType(clazz)
          case cClass =>
            val isStatic = clazz.hasModifierProperty(STATIC)
            val projected = constructTypeForClass(cClass, paramTopLevel, withTypeParameters = !isStatic)(typeArgFun)
            ScProjectionType(projected, clazz)
        }

        if (withTypeParameters && clazz.hasTypeParameters)
          ScParameterizedType(designator, clazz.getTypeParameters.toSeq.zipWithIndex.map(typeArgFun.tupled))
        else designator
    }

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
      case argument: ScExistentialArgument if !visitedExistentialArgs(argument) =>
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


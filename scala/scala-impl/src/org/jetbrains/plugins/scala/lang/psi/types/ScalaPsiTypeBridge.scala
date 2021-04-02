package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.psi.PsiClassType.ClassResolveResult
import com.intellij.psi.PsiModifier.STATIC
import com.intellij.psi._
import com.intellij.psi.impl.source.PsiImmediateClassType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.types.ScalaPsiTypeBridge.RawTypeParamCollector
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType
import org.jetbrains.plugins.scala.lang.psi.types.result.Failure

import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.jdk.CollectionConverters._

trait ScalaPsiTypeBridge extends api.PsiTypeBridge {
  typeSystem: api.TypeSystem =>

  override protected def toScTypeInner(psiType: PsiType,
                                       paramTopLevel: Boolean,
                                       treatJavaObjectAsAny: Boolean,
                                       rawExistentialArguments: Option[RawExistentialArgs]): ScType = psiType match {
    case classType: PsiClassType =>
      val result = classType.resolveGenerics

      if (PsiClassType.isRaw(result))
        return convertJavaRawType(result, paramTopLevel)(rawExistentialArguments)

      result.getElement match {
        case null => Nothing
        case psiTypeParameter: PsiTypeParameter => typeParamType(psiTypeParameter, rawExistentialArguments)
        case clazz if clazz.qualifiedName == "java.lang.Object" =>
          if (paramTopLevel && treatJavaObjectAsAny) Any
          else                                       AnyRef
        case c =>
          val clazz = c match {
            case o: ScObject => ScalaPsiUtil.getCompanionModule(o).getOrElse(o)
            case _           => c
          }

          val substitutor = result.getSubstitutor

          val classTpe =
            constructTypeForClass(clazz) { (typeParam, idx) =>
              substitutor.substitute(typeParam) match {
                case null                              => typeParamType(typeParam, rawExistentialArguments)
                case wildcardType: PsiWildcardType     => existentialArg(s"_$$${idx + 1}", wildcardType, paramTopLevel = true)
                case captured: PsiCapturedWildcardType => existentialArg(s"_$$${idx + 1}", captured.getWildcard, paramTopLevel = true)
                case substed                           => toScTypeInner(substed, paramTopLevel = false, treatJavaObjectAsAny = true, rawExistentialArguments)
              }
            }

          if (rawExistentialArguments.isEmpty) classTpe.unpackedType
          else                                 classTpe
      }
    case wildcardType: PsiWildcardType =>
      existentialArg("_$1", wildcardType, paramTopLevel = false)
    case _: PsiDisjunctionType => Any
    case _ => super.toScTypeInner(psiType, paramTopLevel, treatJavaObjectAsAny, rawExistentialArguments)
  }

  private def convertJavaRawType(
    rawClassResult:     ClassResolveResult,
    paramTopLevel:      Boolean
  )(rawExistentialArgs: Option[RawExistentialArgs]): ScType =
    try {
      val clazz = rawClassResult.getElement

      val quantified = rawExistentialArgs match {
        case None =>
          val map = new RawTypeParamToExistentialMapBuilder(rawClassResult, paramTopLevel).buildMap
          val quantified = constructTypeForClass(clazz) { (tp, _) =>
            map.getOrElse(tp, TypeParameterType(tp))
          }
          map.values.foreach(_.initialize())
          quantified
        case Some(_) =>
          constructTypeForClass(clazz) { (tp, _) =>
            ScExistentialArgument(tp.name, Seq.empty, Nothing, AnyRef)
          }
      }
      quantified.unpackedType
    } catch {
      case e: IllegalStateException =>
        throw new IllegalStateException(
          s"Wrong conversion of java raw type ${rawClassResult.getElement.qualifiedName}",
          e
        )
    }

  def typeParamType(psiTypeParameter: PsiTypeParameter, rawExistentialArgs: Option[RawExistentialArgs]): ScType =
    rawExistentialArgs.flatMap(_.get(psiTypeParameter))
      .getOrElse(TypeParameterType(psiTypeParameter))


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

  private def constructTypeForClass(clazz: PsiClass, withTypeParameters: Boolean = true)
                                   (typeArgFun: (PsiTypeParameter, Int) => ScType): ScType = {

    clazz match {
      case PsiClassWrapper(definition) => constructTypeForClass(definition)(typeArgFun)
      case _ =>
        val designator = clazz.containingClass match {
          case null   => ScDesignatorType(clazz)
          case cClass =>
            val isStatic = clazz.hasModifierProperty(STATIC)
            val projected = constructTypeForClass(cClass, withTypeParameters = !isStatic)(typeArgFun)
            ScProjectionType(projected, clazz)
        }

        if (withTypeParameters && clazz.hasTypeParameters)
          ScParameterizedType(
            designator,
            clazz.getTypeParameters.toSeq.zipWithIndex.map(typeArgFun.tupled)
          )
        else designator
    }

  }

  override def toPsiType(`type`: ScType, noPrimitives: Boolean): PsiType = toPsiTypeInner(`type`, noPrimitives)

  private def toPsiTypeInner(`type`: ScType,
                             noPrimitives: Boolean = false)
                            (implicit
                             visitedAliases: Set[ScTypeAlias] = Set.empty,
                             visitedExistentialArgs: Set[ScExistentialArgument] = Set.empty): PsiType = {

    def outerClassHasTypeParameters(proj: ScProjectionType): Boolean = {
      proj.projected.extractClass match {
        case Some(outer) => outer.hasTypeParameters
        case _ => false
      }
    }

    val t = expandIfAlias(`type`)
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
              toPsiTypeInner(ScParameterizedType(c.designator, args.toSeq), noPrimitives)(visitedAliases + typeAlias.physical, visitedExistentialArgs)
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
            .map(toPsiTypeInner(_, noPrimitives)(visitedAliases + typeAlias.physical, visitedExistentialArgs))
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
            val sup: PsiType = toPsiTypeInner(lower)(visitedAliases, visitedExistentialArgs + argument)
            if (sup.isInstanceOf[PsiWildcardType]) javaObject
            else PsiWildcardType.createSuper(manager, sup)
          }
        } else {
          val psi = toPsiTypeInner(upper)(visitedAliases, visitedExistentialArgs + argument)
          if (psi.isInstanceOf[PsiWildcardType]) javaObject
          else PsiWildcardType.createExtends(manager, psi)
        }

      case std: StdType       => stdToPsiType(std, noPrimitives)
      case lit: ScLiteralType => toPsiTypeInner(lit.wideType)
      case _                  => javaObject
    }
  }

  private def expandIfAlias(scType: ScType): ScType = scType match {
    case AliasType(ta: ScTypeAliasDefinition, _, upper) =>
      upper match {
        case Failure(_) => projectContext.stdTypes.Any
        case Right(u)   => u
      }
    case _ => scType
  }

  private def psiTypeOf(typeParameter: PsiTypeParameter): PsiType =
    PsiSubstitutor.EMPTY.substitute(typeParameter)


  private class RawTypeParamToExistentialMapBuilder(rawClassResult: ClassResolveResult, paramTopLevel: Boolean) {

    def buildMap: Map[PsiTypeParameter, ScExistentialArgument] = lazyMap

    private lazy val lazyMap = {
      val rawTypeParams = collectRawTypeParameters(rawClassResult.getElement, rawClassResult.getSubstitutor)
      rawTypeParams.map { tp =>
        (tp, ScExistentialArgument.deferred(tp.getName, Seq.empty, () => Nothing, () => upperForRaw(tp)))
      }.toMap
    }

    private def upperForRaw(tp: PsiTypeParameter): ScType = {
      def convertBound(bound: PsiType) = {
        toScTypeInner(bound, paramTopLevel, rawExistentialArguments = Some(lazyMap))
      }

      tp.getExtendsListTypes match {
        case Array() => api.Any
        case Array(head) => convertBound(head)
        case components => ScCompoundType(ArraySeq.unsafeWrapArray(components.map(convertBound)))
      }
    }

    private def collectRawTypeParameters(psiClass: PsiClass, rawSubstitutor: PsiSubstitutor): Set[PsiTypeParameter] = {
      val collector = new RawTypeParamCollector(rawSubstitutor)

      for (typeParam <- psiClass.getTypeParameters) {
        collector.visitClassType(new PsiImmediateClassType(typeParam, PsiSubstitutor.EMPTY))

        for (upperBound <- typeParam.getExtendsListTypes) {
          collector.visitClassType(upperBound)
        }
      }
      collector.result
    }
  }
}

private object ScalaPsiTypeBridge {

  private class RawTypeParamCollector(rawTypeSubstitutor: PsiSubstitutor) extends PsiTypeMapper {
    private var rawTypeParameters: Set[PsiTypeParameter] = Set.empty
    private val visited: mutable.Set[PsiClass] = mutable.Set.empty

    def result: Set[PsiTypeParameter] = rawTypeParameters

    override def visitClassType(classType: PsiClassType): PsiType = {
      val resolveResult = classType.resolveGenerics
      val psiClass = resolveResult.getElement

      if (visited.contains(psiClass)) {
        return classType
      }
      else {
        visited.add(psiClass)
      }

      psiClass match {
        case tp: PsiTypeParameter =>
          if (rawTypeSubstitutor.substitute(tp) == null)
            rawTypeParameters += tp

        case _ =>
          for (psiType <- resolveResult.getSubstitutor.getSubstitutionMap.values.asScala) {
            if (psiType != null) psiType.accept(this)
          }
      }

      classType
    }
  }
}


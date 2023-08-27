package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiClassType.ClassResolveResult
import com.intellij.psi._
import com.intellij.psi.impl.source.PsiImmediateClassType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.constructTypeForPsiClass
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types.ScalaPsiTypeBridge.{Log, RawTypeParamCollector}
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType
import org.jetbrains.plugins.scala.lang.psi.types.result.Failure
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.jdk.CollectionConverters._

//noinspection InstanceOf,SpellCheckingInspection
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
            constructTypeForPsiClass(clazz) { (typeParam, idx) =>
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
          val quantified = constructTypeForPsiClass(clazz) { (tp, _) =>
            map.getOrElse(tp, TypeParameterType(tp))
          }
          map.values.foreach(_.initialize())
          quantified
        case Some(_) =>
          constructTypeForPsiClass(clazz) { (tp, _) =>
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

  override def toPsiType(`type`: ScType, noPrimitives: Boolean): PsiType =
    toPsiTypeInner(`type`, noPrimitives)(recursionCallDepth = 0, prevTypeValue = null, prevNoPrimitivesValue = false)

  /**
   * @param recursionCallDepth    helper parameter required for easier debugging of SOE inside recursion
   * @param prevTypeValue         should be the previous value of `type` during recursive calls<br>
   *                              this is a helper parameter to prevent infinite recursion
   * @param prevNoPrimitivesValue should be the previous value of noPrimitives during recursive calls<br>
   *                              this is a helper parameter to prevent infinite recusion
   */
  private def toPsiTypeInner(
    `type`: ScType,
    noPrimitives: Boolean,
  )(
    recursionCallDepth: Int,
    prevTypeValue: ScType,
    prevNoPrimitivesValue: Boolean,
  )(implicit
    visitedAliases: Set[ScTypeAlias] = Set.empty,
    visitedExistentialArgs: Set[ScExistentialArgument] = Set.empty
  ): PsiType = {
    def javaObject: PsiType = createJavaObject

    if ((`type` eq prevTypeValue) && noPrimitives == prevNoPrimitivesValue) {
      val exception = new IllegalStateException(
        s"""Infinite recursion detected while calculating ScalaPsiTypeBridge.toPsiTypeInner
           |recursionCallDepth    : $recursionCallDepth
           |type presentable text : ${prevTypeValue.presentableText(TypePresentationContext.emptyContext)}
           |noPrimitives          : $prevNoPrimitivesValue
           |""".stripMargin.trim
      )
      //don't fail the type inference process, just report the error and return dummy PsiType value
      Log.error(exception)
      return javaObject
    }

    def outerClassHasTypeParameters(proj: ScProjectionType): Boolean = {
      proj.projected.extractClass match {
        case Some(outer) => outer.hasTypeParameters
        case _ => false
      }
    }

    val typeExpanded = expandIfAlias(`type`)
    if (typeExpanded.isInstanceOf[NonValueType])
      return toPsiTypeInner(typeExpanded.inferValueType, noPrimitives = false)(recursionCallDepth + 1, `type`, noPrimitives)

    val qualNameToType = `type`.getProject.stdTypes.QualNameToType

    typeExpanded match {
      case ScCompoundType(Seq(typez, _*), _, _) =>
        toPsiTypeInner(typez, noPrimitives = false)(recursionCallDepth + 1, `type`, noPrimitives)
      case ScDesignatorType(c: ScTypeDefinition) if qualNameToType.contains(c.qualifiedName) =>
        toPsiTypeInner(qualNameToType(c.qualifiedName), noPrimitives)(recursionCallDepth + 1, `type`, noPrimitives)
      case ScDesignatorType(valClass: ScClass) if ValueClassType.isValueClass(valClass) =>
        valClass.parameters.head.getRealParameterType match {
          case Right(tp) if !(noPrimitives && tp.isPrimitive) =>
            toPsiTypeInner(tp, noPrimitives)(recursionCallDepth + 1, `type`, noPrimitives)
          case _ => createType(valClass)
        }
      case ScDesignatorType(c: PsiClass) => createType(c)
      case arrayType(arg) =>
        new PsiArrayType(toPsiTypeInner(arg, noPrimitives = false)(recursionCallDepth + 1, `type`, noPrimitives))
      case ParameterizedType(ScDesignatorType(c: PsiClass), args) =>
        val argsWithTypeParams = args.zip(c.getTypeParameters)
        val subst = argsWithTypeParams.foldLeft(PsiSubstitutor.EMPTY) {
          case (s, (targ, tp)) =>
            s.put(tp, toPsiTypeInner(targ, noPrimitives = true)(recursionCallDepth + 1, `type`, noPrimitives))
        }
        createType(c, subst)
      case ParameterizedType(proj@ScProjectionType(_, _), args) => proj.actualElement match {
        case c: PsiClass =>
          if (c.qualifiedName == "scala.Array" && args.length == 1)
            new PsiArrayType(toPsiTypeInner(args.head, noPrimitives = false)(recursionCallDepth + 1, `type`, noPrimitives))
          else {
            val subst = args.zip(c.getTypeParameters).foldLeft(PsiSubstitutor.EMPTY) {
              case (s, (targ, tp)) => s.put(tp, toPsiTypeInner(targ, noPrimitives = false)(recursionCallDepth + 1, `type`, noPrimitives))
            }
            createType(c, subst, raw = outerClassHasTypeParameters(proj))
          }
        case typeAlias: ScTypeAlias if !visitedAliases.contains(typeAlias.physical) =>
          typeAlias.upperBound match {
            case Right(c: ScParameterizedType) =>
              toPsiTypeInner(
                ScParameterizedType(c.designator, args),
                noPrimitives
              )(
                recursionCallDepth + 1, `type`, noPrimitives
              )(visitedAliases + typeAlias.physical, visitedExistentialArgs)
            case _ => javaObject
          }
        case _ => javaObject
      }
      case ParameterizedType(TypeParameterType.ofPsi(typeParameter), _) => psiTypeOf(typeParameter)
      case proj@ScProjectionType(_, _) => proj.actualElement match {
        case clazz: PsiClass =>
          clazz match {
            case syn: ScSyntheticClass =>
              toPsiTypeInner(syn.stdType, noPrimitives = false)(recursionCallDepth + 1, `type`, noPrimitives)
            case _ =>
              createType(clazz, raw = outerClassHasTypeParameters(proj))
          }
        case typeAlias: ScTypeAlias if !visitedAliases.contains(typeAlias.physical) =>
          typeAlias.upperBound.toOption
            .map(toPsiTypeInner(_, noPrimitives)(recursionCallDepth + 1, `type`, noPrimitives)(visitedAliases + typeAlias.physical, visitedExistentialArgs))
            .getOrElse(javaObject)
        case _ => javaObject
      }
      case ScThisType(clazz) => createType(clazz)
      case TypeParameterType.ofPsi(typeParameter) => psiTypeOf(typeParameter)
      case ex: ScExistentialType =>
        toPsiTypeInner(ex.quantified, noPrimitives)(recursionCallDepth + 1, `type`, noPrimitives)
      case argument: ScExistentialArgument if !visitedExistentialArgs(argument) =>
        val upper = argument.upper
        val manager: PsiManager = projectContext
        if (upper.equiv(Any)) {
          val lower = argument.lower
          if (lower.equiv(Nothing)) PsiWildcardType.createUnbounded(manager)
          else {
            val sup: PsiType = toPsiTypeInner(lower, noPrimitives = false)(recursionCallDepth + 1, `type`, noPrimitives)(visitedAliases, visitedExistentialArgs + argument)
            if (sup.isInstanceOf[PsiWildcardType]) javaObject
            else PsiWildcardType.createSuper(manager, sup)
          }
        } else {
          val psi = toPsiTypeInner(upper, noPrimitives = false)(recursionCallDepth + 1, `type`, noPrimitives)(visitedAliases, visitedExistentialArgs + argument)
          if (psi.isInstanceOf[PsiWildcardType]) javaObject
          else PsiWildcardType.createExtends(manager, psi)
        }

      case std: StdType       => stdToPsiType(std, noPrimitives)
      case lit: ScLiteralType => toPsiTypeInner(lit.wideType, noPrimitives = false)(recursionCallDepth + 1, `type`, noPrimitives)
      case _                  => javaObject
    }
  }

  private def expandIfAlias(scType: ScType): ScType = scType match {
    case AliasType(_: ScTypeAliasDefinition, _, upper) =>
      upper match {
        case Failure(_) => scType.getProject.stdTypes.Any
        case Right(u)   => u
      }
    case _ => scType
  }

  private def psiTypeOf(typeParameter: PsiTypeParameter): PsiType =
    PsiSubstitutor.EMPTY.substitute(typeParameter)

  private object RawTypeParamToExistentialMapBuilder {
    val NoUpperBound: () => StdType = () => api.Any
    val NoLowerBound: () => StdType = () => api.Nothing
  }
  private class RawTypeParamToExistentialMapBuilder(rawClassResult: ClassResolveResult, paramTopLevel: Boolean) {
    import RawTypeParamToExistentialMapBuilder._

    def buildMap: Map[PsiTypeParameter, ScExistentialArgument] = lazyMap

    private lazy val lazyMap = {
      val rawTypeParams = collectRawTypeParameters(rawClassResult.getElement, rawClassResult.getSubstitutor)
      rawTypeParams.map { tp =>
        val upper = upperForRaw(tp)
        val arg = if (upper eq NoUpperBound) {
          // common case: no upper bound, no risk of cycles => just use a Complete type.
          ScExistentialArgument(tp.getName, Seq.empty, NoLowerBound(), NoUpperBound())
        } else
          ScExistentialArgument.deferred(tp.getName, Seq.empty, Some(TypeParameter(tp)), NoLowerBound, upper)
        (tp, arg)
      }.toMap
    }

    private def upperForRaw(tp: PsiTypeParameter): () => ScType = {
      def convertBound(bound: PsiType) = {
        toScTypeInner(bound, paramTopLevel, rawExistentialArguments = Some(lazyMap))
      }

      tp.getExtendsListTypes match {
        case Array() => NoUpperBound
        case Array(head) => () => convertBound(head)
        case components => () => ScCompoundType(ArraySeq.unsafeWrapArray(components.map(convertBound)))
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

object ScalaPsiTypeBridge {

  private val Log =
    Logger.getInstance(classOf[org.jetbrains.plugins.scala.lang.psi.types.ScalaPsiTypeBridge])

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


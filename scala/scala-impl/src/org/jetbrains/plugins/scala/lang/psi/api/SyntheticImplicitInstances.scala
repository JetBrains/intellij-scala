package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector
import org.jetbrains.plugins.scala.lang.psi.types.SmartSuperTypeUtil.TraverseSupers
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{ExtractClass, ParameterizedType, StdTypes, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScCompoundType, ScLiteralType, ScType, ScalaType, SmartSuperTypeUtil}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.util.CommonQualifiedNames

import scala.jdk.CollectionConverters.CollectionHasAsScala

object SyntheticImplicitInstances {
  val ClassManifest = "scala.reflect.ClassManifest"
  val Manifest      = "scala.reflect.Manifest"
  val OptManifest   = "scala.reflect.OptManifest"
  val ClassTag      = "scala.reflect.ClassTag"
  val TypeTag       = "scala.reflect.api.TypeTags.TypeTag"
  val WeakTypeTag   = "scala.reflect.api.TypeTags.WeakTypeTag"

  val tagsAndManifists: Set[String] =
    Set(ClassManifest, Manifest, OptManifest, ClassTag, TypeTag, WeakTypeTag)

  /**
   * Tags which the compiler can only materialize for a type it is able to erase, i.e. not for an abstract type.
   * `OptManifest` is excluded because `NoManifest` is always available, and `WeakTypeTag` because it is
   * specifically meant to be materializable for abstract types as well.
   */
  private val tagsRequiringConcreteType: Set[String] =
    Set(ClassManifest, Manifest, ClassTag, TypeTag)

  val ValueOf         = "scala.ValueOf"
  val TypeTest        = "scala.reflect.TypeTest"
  val ConformsWitness = "scala.Predef.<:<"
  val EquivWitness    = "scala.Predef.=:="
  val Mirror          = "scala.deriving.Mirror"
  val MirrorProduct   = "scala.deriving.Mirror.Product"
  val MirrorSum       = "scala.deriving.Mirror.Sum"
  val Mirrors         = Seq(Mirror, MirrorProduct, MirrorSum)
  val CanEqual        = "scala.CanEqual"

  private def tupleTypeText(elements: Seq[String]): String =
    elements match {
      case Seq()       => "EmptyTuple"
      case Seq(single) => s"Tuple1[$single]"
      case _           => elements.mkString("(", ", ", ")")
    }

  def mirrorType(
    targetType: ScType,
    descriptor: MirrorDescriptor,
    mirrorFqn:  String,
    context:    PsiElement
  ): Option[ScType] = {
    // In Scala 3, Mirror.Singleton extends Mirror.Product.
    val isProduct = descriptor.kind != MirrorKind.Sum
    val isSum     = descriptor.kind == MirrorKind.Sum

    if (mirrorFqn == MirrorProduct && !isProduct) None
    else if (mirrorFqn == MirrorSum && !isSum)    None
    else {
      val mirroredType       = targetType.canonicalText
      val mirroredElemTypes  = tupleTypeText(descriptor.elemTypes.map(_.canonicalText))
      val mirroredElemLabels = tupleTypeText(descriptor.elemLabels.map(fieldName => s"\"$fieldName\""))

      ScalaPsiElementFactory.createTypeFromText(
        s"""$mirrorFqn {
           | type MirroredType       = $mirroredType;
           | type MirroredMonoType   = $mirroredType;
           | type MirroredLabel      = "${descriptor.name}";
           | type MirroredElemTypes  = $mirroredElemTypes;
           | type MirroredElemLabels = $mirroredElemLabels;
           |}""".stripMargin, context, null
      )
    }
  }

  private[api] def compilerGeneratedInstance(
    tp:    ScType,
    place: PsiElement
  )(implicit
    context: Context
  ): Option[ScalaResolveResult] =
    tp.removeAliasDefinitions() match {
      case p @ ParameterizedType(_, params) =>
        p.extractClass.collect {
          case clazz if areEligible(params, clazz.qualifiedName, place) =>
            new ScalaResolveResult(clazz, p.substitutor)
        }
      case ScCompoundType(Seq(ExtractClass(cls)), _, typesMap) if Mirrors.contains(cls.qualifiedName) =>
        typesMap
          .get("MirroredMonoType")
          .map(sig => sig.typeAlias -> sig.substitutor)
          .flatMap {
            case (tdef: ScTypeAliasDefinition, subst) if !tdef.isEffectivelyOpaque =>
              val targetType       = subst(tdef.aliasedType.getOrAny)
              val mirrorDescriptor = mirrorDescriptorFor(targetType)

              for {
                descriptor <- mirrorDescriptor
                resultType <- mirrorType(targetType, descriptor, cls.qualifiedName, place)
              } yield new ScalaResolveResult(cls, inferredType = resultType.toOption)
            case _ => None
          }
      case _ => None
    }


  private def areEligible(
    params:  Seq[ScType],
    typeFqn: String,
    place:   PsiElement
  )(implicit
    context: Context
  ): Boolean =
    (typeFqn, params) match {
      case (TypeTest, Seq(lhs, rhs))      => eligibleForTypeTest(lhs, rhs)
      case (ValueOf, Seq(t))              => eligibleForValueOf(t)
      case (ConformsWitness, Seq(t1, t2)) => t1.conforms(t2)
      case (EquivWitness, Seq(t1, t2))    => t1.equiv(t2)
      case (CanEqual, Seq(lhs, rhs))      => eligibleForSyntheticCanEqual(lhs, rhs, place)
      case (_, Seq(t))                    => tagsAndManifists.contains(typeFqn) && eligibleForTag(typeFqn, t)
      case _                              => false
    }

  /**
   * The compiler materializes a tag by erasing its type argument, which it cannot do for an abstract type.
   * In such a case there has to be an existing value to refer to (e.g. a context bound evidence parameter),
   * otherwise the tag is simply not available.
   */
  private def eligibleForTag(tagFqn: String, argType: ScType)(implicit context: Context): Boolean =
    !tagsRequiringConcreteType.contains(tagFqn) || !isAbstract(argType.removeAliasDefinitions())

  private def isAbstract(t: ScType): Boolean = t match {
    case _: TypeParameterType                           => true
    case ScProjectionType(_, _: ScTypeAliasDeclaration) => true
    case ScDesignatorType(_: ScTypeAliasDeclaration)    => true
    case _                                              => false
  }

  private def eligibleForSyntheticCanEqual(
    lhs: ScType,
    rhs: ScType,
    place: PsiElement
  ): Boolean = {
    val strictEqualityEnabled = place.isStrictEqualityEnabled

    def derivesFrom(scType: ScType, fqn: String): Boolean = {
      var res: Boolean = false

      SmartSuperTypeUtil.traverseSuperTypes(
        scType,
        (_, cls, _) => cls match {
          case cls if cls.qualifiedName == fqn =>
            res = true
            TraverseSupers.Stop
          case _ => TraverseSupers.ProcessParents
        }
      )

      res
    }

    def compareToBoxed(l: ScType, r: ScType): Boolean = {
      val boxedNumericClass = {
        val maybeEntry = StdTypes.instance(place.getProject).fqnBoxedToScType.find {
          case (_, tpe) => tpe == l
        }

        maybeEntry.flatMap { case (fqn, _) =>
          ScalaPsiManager
            .instance(place.getProject)
            .getCachedClass(place.getResolveScope, fqn)
        }
      }

      boxedNumericClass.isDefined &&
        r.extractClass == boxedNumericClass
    }

    val canEqualForPredefinedClasses = {
      val dealiasedLhs = lhs.removeAliasDefinitionsIn(place)
      val dealiasedRhs = rhs.removeAliasDefinitionsIn(place)

      if (dealiasedLhs.isNothing || dealiasedRhs.isNothing) true
      else if (dealiasedLhs.isUnit && dealiasedRhs.isUnit) true
      else if (dealiasedLhs.isPrimitive) {
        if (dealiasedRhs.isPrimitive)
          dealiasedLhs == dealiasedRhs || dealiasedLhs.isNumericType && dealiasedRhs.isNumericType
        else compareToBoxed(dealiasedLhs, dealiasedRhs)
      }
      else if (dealiasedRhs.isPrimitive) compareToBoxed(dealiasedRhs, dealiasedLhs)
      else if (dealiasedLhs.isNull) dealiasedLhs == dealiasedRhs || derivesFrom(dealiasedRhs, "java.lang.Object")
      else if (dealiasedRhs.isNull) derivesFrom(dealiasedLhs, "java.lang.Object")
      else false
    }

    canEqualForPredefinedClasses ||
      (!strictEqualityEnabled && {
        // if lhs == rhs, no need to check for CanEqual[lhs, lhs] or CanEqual[rhs, rhs],
        // if they were to exist, we wouldn't have come here
        lhs.equiv(rhs) ||
          !hasCanEqual(lhs, place) && !hasCanEqual(rhs, place)
      })
  }

  private def hasCanEqual(argType: ScType, place: PsiElement): Boolean = {
    val typeText = argType.canonicalText

    val instanceType =
      ScalaPsiElementFactory.createTypeFromText(s"scala.CanEqual[$typeText, $typeText]", place, place)

    instanceType.exists { tpe =>
      val collector     = new ImplicitCollector(place, tpe, tpe, None, false)
      val searchResults = collector.collect()
      searchResults.size == 1
    }
  }

  private def eligibleForTypeTest(lhs: ScType, rhs: ScType): Boolean =
    !rhs.isAnyVal &&
      !rhs.isAnyRef &&
      !rhs.extractClass.exists(_.qualifiedName == CommonQualifiedNames.JavaLangObjectFqn) &&
      lhs.isPrimitive == rhs.isPrimitive


  private case class MirrorDescriptor(
    kind:       MirrorKind,
    name:       String,
    elemLabels: Seq[String],
    elemTypes:  Seq[ScType]
  )

  private sealed trait MirrorKind
  private object MirrorKind {
    case object Product   extends MirrorKind
    case object Sum       extends MirrorKind
    case object Singleton extends MirrorKind
  }

  private def mirrorDescriptorFor(tpe: ScType): Option[MirrorDescriptor] =
    tpe.extractDesignatedType(expandAliases = true).flatMap {
      case (designated, subst) =>
        mirrorDescriptorFor(designated, subst)
    }

  private def mirrorDescriptorFor(
    designated: PsiNamedElement,
    subst:      ScSubstitutor
  ): Option[MirrorDescriptor] =
    designated match {
      case obj: ScObject => // also covers ScEnumSingletonCase
        Option.when(obj.isCase)(
          MirrorDescriptor(MirrorKind.Singleton, obj.name, Seq.empty, Seq.empty)
        )
      case enum: ScEnum =>
        val cases     = enum.cases
        val caseNames = cases.map(_.name)
        val caseTypes = cases.map(cse => subst(cse.`type`().getOrAny))

        MirrorDescriptor(
          MirrorKind.Sum,
          enum.name,
          caseNames,
          caseTypes
        ).toOption
      case cls: ScClass if cls.isCase => // this branch also covers ScEnumClassCase
        val consOption       = cls.constructor
        val consParamClauses = consOption.map(_.effectiveParameterClauses).getOrElse(Seq.empty)

        Option.when(consParamClauses.size == 1) {
          val params     = consParamClauses.head.parameters
          val paramNames = params.map(_.name)
          val paramTypes = params.map(param => subst(param.outsideParamType.getOrAny))

          MirrorDescriptor(
            MirrorKind.Product,
            cls.name,
            paramNames,
            paramTypes
          )
        }
      case tdef: ScTypeDefinition if tdef.isSealed =>
        val inheritorsDescriptors =
          ClassInheritorsSearch.search(
            tdef,
            new LocalSearchScope(tdef.getContainingFile),
            true
          ).mapping { cls =>
              val tdef           = cls.asInstanceOf[ScTypeDefinition]
              val designatorType = tdef.`type`().getOrElse(return None)
              val clsDescriptor  = mirrorDescriptorFor(designatorType)

              if (clsDescriptor.isEmpty) return None
              else                       (tdef, designatorType)
            }.findAll()
            .asScala
            .toSeq
            .sortBy { case (tdef, _) =>
              //This is later stored in a tuple, so order matters
              //(must be consistent with order of declaration in file).
              //ClassInheritorsSearch gives no such guarantee.
              tdef.getTextOffset
            }

        MirrorDescriptor(
          MirrorKind.Sum,
          tdef.name,
          inheritorsDescriptors.map(_._1.name),
          inheritorsDescriptors.map(_._2)
        ).toOption
      case _ => None
    }

  private def eligibleForValueOf(t: ScType)(implicit context: Context): Boolean = {
    t.removeAliasDefinitions().inferValueType match {
      case _: ScLiteralType         => true
      case _ if t.isUnit            => true
      case _: ScThisType            => true
      case tpt: TypeParameterType   => eligibleForValueOf(tpt.upperType)
      case ScCompoundType(cs, _, _) => cs.exists(eligibleForValueOf)
      case valueType                => isStable(valueType)
    }
  }

  private def isStable(t: ScType): Boolean = {
    val designator = t match {
      case ScProjectionType(_, td: ScTypedDefinition) => Some(td)
      case ScDesignatorType(td: ScTypedDefinition)    => Some(td)
      case _ => None
    }
    designator.exists(d => d.isStable && ScalaPsiUtil.hasStablePath(d))
  }

}

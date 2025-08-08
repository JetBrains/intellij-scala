package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.psi.PsiClass
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum, ScObject}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{ExtractClass, ParameterizedType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScCompoundType, ScLiteralType, ScType}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.util.CommonQualifiedNames

object SyntheticImplicitInstances {
  val tagsAndManifists: Set[String] = Set(
    "scala.reflect.ClassManifest",
    "scala.reflect.Manifest",
    "scala.reflect.OptManifest",
    "scala.reflect.ClassTag",
    "scala.reflect.api.TypeTags.TypeTag",
    "scala.reflect.api.TypeTags.WeakTypeTag"
  )

  val ValueOf         = "scala.ValueOf"
  val TypeTest        = "scala.reflect.TypeTest"
  val ConformsWitness = "scala.Predef.<:<"
  val EquivWitness    = "scala.Predef.=:="
  val Mirrors         = Seq("scala.deriving.Mirror", "scala.deriving.Mirror.Product", "scala.deriving.Mirror.Sum")

  private[api] def compilerGeneratedInstance(tp: ScType)(implicit context: Context): Option[ScalaResolveResult] =
    tp.removeAliasDefinitions() match {
      case p @ ParameterizedType(_, params) =>
        p.extractClass.collect {
          case clazz if areEligible(params, clazz.qualifiedName) =>
            new ScalaResolveResult(clazz, p.substitutor)
        }
      case ScCompoundType(Seq(ExtractClass(cls)), _, typesMap) if Mirrors.contains(cls.qualifiedName) =>
        typesMap
          .get("MirroredMonoType")
          .map(sig => sig.typeAlias -> sig.substitutor)
          .collect {
            case (tdef: ScTypeAliasDefinition, subst) if !tdef.isEffectivelyOpaque && eligibleForMirror(subst(tdef.aliasedType.getOrAny)) =>
              new ScalaResolveResult(cls)
          }
      case _ => None
    }


  private def areEligible(params: Seq[ScType], typeFqn: String)(implicit context: Context): Boolean =
    (typeFqn, params) match {
      case (TypeTest, Seq(lhs, rhs))                    => eligibleForTypeTest(lhs, rhs)
      case (ValueOf, Seq(t))                            => eligibleForValueOf(t)
      case (ConformsWitness, Seq(t1, t2))               => t1.conforms(t2)
      case (EquivWitness, Seq(t1, t2))                  => t1.equiv(t2)
      case (mirror, Seq(t)) if Mirrors.contains(mirror) => eligibleForMirror(t)
      case _ if params.size == 1                        => tagsAndManifists.contains(typeFqn)
      case _                                            => false
    }

  private def eligibleForTypeTest(lhs: ScType, rhs: ScType): Boolean =
    !rhs.isAnyVal &&
      !rhs.isAnyRef &&
      !rhs.extractClass.exists(_.qualifiedName == CommonQualifiedNames.JavaLangObjectFqn) &&
      lhs.isPrimitive == rhs.isPrimitive

  private def eligibleForMirror(tpe: ScType)(implicit context: Context): Boolean = {
    tpe.extractDesignated(expandAliases = true) match {
      case Some(des) => des match {
        case obj: ScObject                   => obj.isCase
        case _: ScEnum                       => true
        case _: ScEnumCase                   => true
        case cls: ScClass if cls.isCase      => true
        case tdef: PsiClass if tdef.isSealed =>
          ClassInheritorsSearch.search(
            tdef,
            new LocalSearchScope(tdef.getContainingFile),
            true
          ).allMatch(cls => eligibleForMirror(ScDesignatorType(cls)))
        case _ => false
      }
      case _ => false
    }
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

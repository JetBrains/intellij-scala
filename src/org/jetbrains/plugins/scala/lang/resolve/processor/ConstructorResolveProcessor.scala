package org.jetbrains.plugins.scala
package lang.resolve.processor

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScTypeExt}
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult}

import scala.collection.Set

/**
  * User: Alexander Podkhalyuzin
  * Date: 30.04.2010
  */
class ConstructorResolveProcessor(constr: PsiElement, refName: String, args: List[Seq[Expression]],
                                  typeArgs: Seq[ScTypeElement], kinds: Set[ResolveTargets.Value],
                                  shapeResolve: Boolean, allConstructors: Boolean)
  extends MethodResolveProcessor(constr, refName, args, typeArgs, Seq.empty, kinds,
    isShapeResolve = shapeResolve, enableTupling = true) {

  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    val fromType = getFromType(state)

    val initialSubstitutor = getSubst(state)
    val defaultSubstitutor = fromType map {
      initialSubstitutor.followUpdateThisType
    } getOrElse initialSubstitutor

    if (nameAndKindMatch(named, state)) {
      val accessible = isAccessible(named, ref)
      if (accessibility && !accessible) return true

      def constructorIsAccessible(constructor: PsiMethod) =
        isAccessible(constructor, ref)

      def constructors(clazz: PsiClass, substitutor: ScSubstitutor) = clazz.constructors filter {
        case constructor if accessibility =>
          constructorIsAccessible(constructor)
        case _ => true
      } map {
        (_, substitutor, Some(named))
      }

      def orDefault(tuples: Seq[(PsiNamedElement, ScSubstitutor, Option[PsiNamedElement])] = Seq.empty) =
        tuples match {
        case Seq() => Seq((named, defaultSubstitutor, None))
        case seq => seq
      }

      val tuples: Seq[(PsiNamedElement, ScSubstitutor, Option[PsiNamedElement])] = named match {
        case clazz: PsiClass =>
          orDefault(constructors(clazz, defaultSubstitutor))
        case _: ScTypeAliasDeclaration =>
          orDefault()
        case definition: ScTypeAliasDefinition =>
          val result = definition.aliasedType.toOption.toSeq flatMap {
            _.extractClassType
          } flatMap {
            case (clazz, substitutor) =>
              constructors(clazz, defaultSubstitutor.followed(substitutor))
          }

          orDefault(result)
        case _ => Seq()
      }

      addResults(tuples map {
        case (namedElement, substitutor, parentElement) =>
          val elementIsAccessible = namedElement match {
            case _: ScTypeAliasDefinition => true
            case constructor: PsiMethod if accessible =>
              constructorIsAccessible(constructor)
            case _ => accessible
          }

          new ScalaResolveResult(namedElement,
            substitutor,
            getImports(state),
            Option(state.get(ResolverEnv.nameKey)),
            parentElement = parentElement,
            boundClass = getBoundClass(state),
            fromType = fromType,
            isAccessible = elementIsAccessible)
      })
    }

    true
  }

  override def candidatesS: Set[ScalaResolveResult] = {
    def updateResult(result: ScalaResolveResult) =
      new ScalaResolveResult(result.getActualElement,
        result.substitutor,
        result.importsUsed,
        boundClass = result.boundClass,
        fromType = result.fromType,
        isAccessible = result.isAccessible)

    val candidates = super.candidatesS
    candidates.toSeq match {
      case _ if allConstructors => candidates
      case Seq() => Set.empty
      case Seq(result: ScalaResolveResult) => Set(result)
      case _ => candidates.map(updateResult)
    }
  }
}

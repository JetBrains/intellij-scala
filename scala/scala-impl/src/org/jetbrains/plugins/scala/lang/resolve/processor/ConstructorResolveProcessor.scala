package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.psi._
import com.intellij.psi.impl.light.LightDefaultConstructor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScConstructorOwner
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult}

class ConstructorResolveProcessor(
  constructorRef:  ScStableCodeReference,
  refName:         String,
  args:            List[Seq[Expression]],
  typeArgs:        Seq[ScTypeElement],
  kinds:           Set[ResolveTargets.Value],
  shapeResolve:    Boolean,
  allConstructors: Boolean
) extends MethodResolveProcessor(
  constructorRef,
  refName,
  args,
  typeArgs,
  Seq.empty,
  kinds,
  isShapeResolve = shapeResolve,
  enableTupling = true
) {

  override protected def execute(namedElement: PsiNamedElement)
                                (implicit state: ResolveState): Boolean = {
    val fromType           = state.fromType
    val defaultSubstitutor = state.substitutorWithThisType

    if (nameMatches(namedElement)) {
      val accessible = isAccessible(namedElement, ref)
      if (accessibility && !accessible) return true

      def constructorIsAccessible(constructor: PsiMethod) =
        isAccessible(constructor, ref)

      def constructors(cls: PsiClass, substitutor: ScSubstitutor) = {
        val allConstructors = cls match {
          case scCls: ScConstructorOwner => scCls.constructors
          case javaCls                   =>
            val explicitConstructors = javaCls.constructors

            if (explicitConstructors.isEmpty) LightDefaultConstructor.create(javaCls).toOption.toSeq
            else                              explicitConstructors
        }

        allConstructors.collect {
          case cons if accessibility && constructorIsAccessible(cons) =>
            (cons, substitutor, Some(namedElement))
        }
      }

      def orDefault(
        tuples: Seq[(PsiNamedElement, ScSubstitutor, Option[PsiNamedElement])] = Seq.empty
      ): Seq[(PsiNamedElement, ScSubstitutor, Option[PsiNamedElement])] =
        if (tuples.isEmpty) Seq((namedElement, defaultSubstitutor, None))
        else                tuples

      val tuples = namedElement match {
        case clazz: PsiClass           => orDefault(constructors(clazz, defaultSubstitutor))
        case _: ScTypeAliasDeclaration => orDefault()
        case definition: ScTypeAliasDefinition if definition.isEffectivelyOpaque => orDefault()
        case definition: ScTypeAliasDefinition =>
          val result = definition.aliasedType.toOption.toSeq flatMap {
            _.extractClassType
          } flatMap {
            case (clazz, substitutor) =>
              constructors(clazz, substitutor.followed(defaultSubstitutor))
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
            state.importsUsed,
            state.renamed,
            parentElement = parentElement,
            fromType      = fromType,
            isAccessible  = elementIsAccessible)
      })
    }

    true
  }

  override def candidatesS: Set[ScalaResolveResult] = {
    def updateResult(result: ScalaResolveResult): ScalaResolveResult =
      new ScalaResolveResult(
        result.getActualElement,
        result.substitutor,
        result.importsUsed,
        fromType     = result.fromType,
        isAccessible = result.isAccessible
      )

    val candidates = super.candidatesS

    candidates.toSeq match {
      case _ if allConstructors => candidates
      case Seq()                => Set.empty
      case Seq(result)          => Set(result)
      case _                    => candidates.map(updateResult)
    }
  }
}

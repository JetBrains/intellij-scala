package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, SmartPsiElementPointer}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScMacroDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

import scala.ref.WeakReference

trait ElementUsage {
  def targetCanBePrivate: Boolean
}

private object ElementUsageWithUnknownReference extends ElementUsage {
  override val targetCanBePrivate: Boolean = false
}

private final class ElementUsageWithKnownReference private(
  reference: SmartPsiElementPointer[PsiElement],
  target: WeakReference[ScNamedElement]
) extends ElementUsage {

  def referenceIsInMemberThatHasTypeDefAsAncestor(typeDef: ScTypeDefinition): Boolean = {
    val memberThatReferenceIsPartOf = reference.getElement.parentOfType[ScMember]
    memberThatReferenceIsPartOf.exists(PsiTreeUtil.isAncestor(typeDef, _, /*strict=*/ false))
  }

  private def isReferenceToDefMacroImpl: Boolean =
    reference.getElement.asOptionOf[ScStableCodeReference].exists(ScMacroDefinition.isMacroImplReference)

  /**
   * Returns `true` if [[reference]] does not allow [[target]] to be private due to [[reference]] referring to an
   * extension method defined in the same class as [[reference]] itself, but invoking the extension method via an
   * implicitly summoned instance of the class, rather than invoking it directly as the plain old method that is
   * available in [[reference]]'s immediate scope because [[reference]] and [[target]] are siblings.
   *
   * Below we can see some examples for each scenario.
   * {{{
   * object foo {
   *   implicit class IntExt1(i: Int) { self =>
   *     private val anInt = 42
   *
   *     def addOne: Int = i + 1
   *
   *     // These public methods don't allow `addOne` method to be private.
   *     // Commenting these methods will allow us to mark `addOne` as private.
   *     def addOneCanNotBePrivate1 = i.doStuff.addOne
   *     def addOneCanNotBePrivate2 = Seq(1).map(_.addOne)
   *     def addOneCanNotBePrivate3 = 1.addOne
   *     def addOneCanNotBePrivate4 = anInt.addOne
   *
   *     // With the above methods commented, these can stay public and
   *     // `addOne` can still be marked as private.
   *     def addOneCanBePrivate1 = addOne
   *     def addOneCanBePrivate2 = addOne.doStuff
   *     def addOneCanBePrivate3 = this.addOne
   *     def addOneCanBePrivate4 = self.addOne
   *
   *   }
   *
   *   implicit class IntExt2(i: Int) { def doStuff: Int = 42 }
   * }
   * }}}
   *
   * If you have inspected many Scala PSI element trees, you may notice a pattern: the most common cases can be
   * distinguished by checking whether a reference expression has 1 child or not. If it has exactly one child, this
   * child will be a PsiIdentifier representing the name of the extension method that is referenced. More importantly,
   * if it has exactly one child, there is no way for the reference expression to be a period-separated chain of
   * expressions. So in this case we are sure that this expression concerns an invocation against `this`, which is
   * exactly what we want to know when inspecting implicit class extension method usage from within that very class,
   * and when figuring out if an extension method can be private.
   *
   * So the below method primarily relies on counting children. However, if we would only do this check, some false
   * negatives naturally crop up for these cases:
   * {{{
   *   def addOneCanBePrivate3 = this.addOne
   *   def addOneCanBePrivate4 = self.addOne
   * }}}
   *
   * Implementation Note:<br>
   * This way of utilizing extension methods is probably quite rare.
   * We could ignore these cases, because false negatives are relatively fine to have.
   * But for now I've implemented an additional check in `firstChildIsNonThisTypedExpression`.
   * If we suspect any disproportionate performance impact, we could simply remove this additional check and allow the
   * false negatives.
   */
  private def isIndirectReferenceToImplicitClassExtensionMethodFromWithinThatClass(typeDef: ScTypeDefinition): Boolean = {

    def firstChildIsNonThisTypedExpression(refExpr: ScReferenceExpression): Boolean =
      refExpr.getFirstChild match {
        case e: ScExpression =>
          (e.`type`(), typeDef.`type`()) match {
            case (Right(t1), Right(t2)) => !t1.conforms(t2)
            case _ => false
          }
        case _ => false
      }

    typeDef.hasModifierPropertyScala("implicit") &&
      reference.getElement.asOptionOf[ScReferenceExpression].exists { refExpr =>
        refExpr.children.size != 1 && firstChildIsNonThisTypedExpression(refExpr)
      }
  }

  private def referenceIsInCompanionScope: Boolean = {
    val targetElement = target.underlying.get()

    val targetContainer = targetElement.parentOfType[ScTypeDefinition].orElse(targetElement.asOptionOf[ScTypeDefinition])

    val targetContainerCompanion = targetContainer.flatMap(_.baseCompanion)
    val refElement = reference.getElement
    val referenceContainer = refElement.parentOfType[ScTypeDefinition]
    val referenceContainerIsTargetContainerCompanion = targetContainerCompanion.exists(referenceContainer.contains)

    /**
     * {{{
     * class Bar { Bar.b }
     * object Bar { val b = 42 }
     * }}}
     *
     * This method returns `true` for an `ElementUsage` where `b` in `val b` is our target,
     * and `Bar.b` is our reference to that target.
     */
    def referenceIsExpressionWhoseFirstChildHasTargetContainerType = refElement match {
      case refExpr: ScReferenceExpression if refExpr.children.size > 1 =>
        val firstChild = refExpr.getFirstChild
        val firstChildType = firstChild.asOptionOfUnsafe[Typeable].flatMap(_.`type`().toOption)
        val targetContainerType = targetContainer.flatMap(_.`type`().toOption)

        (firstChildType, targetContainerType) match {
          case (Some(t1), Some(t2)) => t1.equiv(t2)
          case _ => false
        }

      case _ => false
    }

    def isReferenceToImportedCompanionObjectMember: Boolean =
      refElement.is[ScReferenceExpression] && refElement.children.size == 1

    referenceContainerIsTargetContainerCompanion &&
      (refElement.is[ScStableCodeReference] || referenceIsExpressionWhoseFirstChildHasTargetContainerType ||
        isReferenceToImportedCompanionObjectMember)
  }

  override lazy val targetCanBePrivate: Boolean = {

    val parentTypeDef = target.underlying.get().parentOfType[ScTypeDefinition]

    !isReferenceToDefMacroImpl &&
      !parentTypeDef.exists(isIndirectReferenceToImplicitClassExtensionMethodFromWithinThatClass) &&
      (referenceIsInCompanionScope || parentTypeDef.exists(referenceIsInMemberThatHasTypeDefAsAncestor))
  }

  override def toString: String = {
    def location(e: PsiElement): String = s"${e.getContainingFile.name}:${e.getLineNumber + 1}"
    s"ElementUsageWithKnownReference(reference = ${reference.getElement.getText} in ${location(reference.getElement)}, target = ${target.get.map(t => s"${t.name} in ${location(t)}").orNull})"
  }
}

private object ElementUsageWithKnownReference {
  def apply(reference: PsiElement, target: ScNamedElement): ElementUsageWithKnownReference =
    new ElementUsageWithKnownReference(reference.createSmartPointer, WeakReference(target))

  def apply(reference: SmartPsiElementPointer[PsiElement], target: ScNamedElement): ElementUsageWithKnownReference =
    new ElementUsageWithKnownReference(reference, WeakReference(target))
}

package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.psi.{PsiElement, SmartPsiElementPointer}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScMacroDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}

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

  def referenceIsInMemberThatHasTypeDefAsAncestor(typeDefs: ScTypeDefinition*): Boolean = {
    val memberThatReferenceIsPartOf = reference.getElement.parentOfType[ScMember]
    memberThatReferenceIsPartOf.exists(m => typeDefs.exists(typeDef => m == typeDef || typeDef.isAncestorOf(m)))
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
   *
   * {{{
   * object foo {
   *   implicit class IntExt1(i: Int) { self =>
   *     private val anInt = 42
   *
   *     def addOne: Int = i + 1
   *
   *     def addOneCanBePrivate1 = addOne
   *     def addOneCanBePrivate2 = addOne.doStuff
   *     def addOneCanBePrivate3 = this.addOne
   *     def addOneCanBePrivate4 = self.addOne
   *
   *     def addOneCanNotBePrivate1 = i.doStuff.addOne
   *     def addOneCanNotBePrivate2 = Seq(1).map(_.addOne)
   *     def addOneCanNotBePrivate3 = 1.addOne
   *     def addOneCanNotBePrivate4 = anInt.addOne
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
   * Since this way of utilizing extension methods is probably quite rare, and false negatives are relatively fine to
   * have, we could consider forgetting about these cases. For now I've implemented an additional check in
   * `firstChildIsNonThisTypedExpression`. If we suspect any disproportionate performance impact, we could simply
   * remove this additional check and allow the false negatives.
   */
  private def isIndirectReferenceToImplicitClassExtensionMethodFromWithinThatClass(typeDef: ScTypeDefinition): Boolean = {

    def firstChildIsNonThisTypedExpression(refExpr: ScReferenceExpression): Boolean =
      refExpr.children.toSeq.headOption match {
        case Some(e: ScExpression) =>
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

  override def targetCanBePrivate: Boolean = {
    val parentTypeDef = target.underlying.get().parentOfType[ScTypeDefinition]

    !isReferenceToDefMacroImpl &&
      !parentTypeDef.exists(isIndirectReferenceToImplicitClassExtensionMethodFromWithinThatClass) &&
      parentTypeDef.exists { typeDef =>
        val typeDefAndCompanion = typeDef +: typeDef.baseCompanion.toSeq
        referenceIsInMemberThatHasTypeDefAsAncestor(typeDefAndCompanion: _*)
      }
  }
}

private object ElementUsageWithKnownReference {
  def apply(reference: PsiElement, target: ScNamedElement): ElementUsageWithKnownReference =
    new ElementUsageWithKnownReference(reference.createSmartPointer, WeakReference(target))

  def apply(reference: SmartPsiElementPointer[PsiElement], target: ScNamedElement): ElementUsageWithKnownReference =
    new ElementUsageWithKnownReference(reference, WeakReference(target))
}

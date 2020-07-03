package org.jetbrains.plugins.scala
package lang
package completion
package global

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaImportingInsertHandler
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

private[completion] trait CompanionObjectMembersFinder[T <: ScTypedDefinition] {

  this: GlobalMembersFinder =>

  // todo import, class scope import setting reconsider

  override protected def candidates: Iterable[GlobalMemberResult] = for {
    ClassOrTrait(CompanionModule(targetObject)) <- findTargets

    member <- targetObject.members
    if isAccessible(member)

    namedElement <- namedElementsIn(member)
  } yield createResult(
    new ScalaResolveResult(namedElement),
    targetObject
  )

  protected def findTargets: Iterable[PsiElement]

  protected def namedElementsIn(member: ScMember): Seq[T]

  protected def createResult(resolveResult: ScalaResolveResult,
                             classToImport: ScObject): GlobalMemberResult

}

private[completion] object CompanionObjectMembersFinder {

  final class Regular(override protected val place: ScReferenceExpression,
                      accessAll: Boolean)
    extends GlobalMembersFinder(place, accessAll)
      with CompanionObjectMembersFinder[ScTypedDefinition] {

    override protected def findTargets: Iterable[PsiElement] =
      place.withContexts.toIterable

    override protected def namedElementsIn(member: ScMember): Seq[ScTypedDefinition] = member match {
      case value: ScValueOrVariable => value.declaredElements
      case function: ScFunction if !function.isConstructor => Seq(function)
      case _ => Seq.empty
    }

    override protected def createResult(resolveResult: ScalaResolveResult,
                                        classToImport: ScObject): GlobalMemberResult =
      CompanionMemberResult(resolveResult, classToImport)

    private final case class CompanionMemberResult(override val resolveResult: ScalaResolveResult,
                                                   override val classToImport: ScObject)
      extends GlobalMemberResult(resolveResult, classToImport) {

      override protected def buildItem(lookupItem: ScalaLookupItem,
                                       shouldImport: Boolean): Option[ScalaLookupItem] =
        if (shouldImport)
          super.buildItem(lookupItem, shouldImport)
        else
          None

      override protected def createInsertHandler: ScalaImportingInsertHandler =
        new ScalaImportingInsertHandler(classToImport) {

          override protected def qualifyAndImport(reference: ScReferenceExpression): Unit =
            qualifyOnly(reference)
        }
    }
  }
}
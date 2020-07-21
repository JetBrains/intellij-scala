package org.jetbrains.plugins.scala
package lang
package completion
package global

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{OptionExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

private[completion] trait CompanionObjectMembersFinder[T <: ScTypedDefinition] {

  this: GlobalMembersFinder =>

  // todo import, class scope import setting reconsider

  override protected def candidates: Iterable[GlobalMemberResult] = for {
    ScalaObject(targetObject) <- findTargets

    member <- targetObject.members
    if isAccessible(member)

    namedElement <- namedElementsIn(member)
  } yield createResult(
    new ScalaResolveResult(namedElement),
    targetObject
  )

  protected def findTargets: Iterable[PsiElement] =
    place.withContexts.toIterable

  protected def namedElementsIn(member: ScMember): Seq[T]

  protected def createResult(resolveResult: ScalaResolveResult,
                             classToImport: ScObject): GlobalMemberResult

  private object ScalaObject {

    def unapply(definition: ScTypeDefinition): Option[ScObject] = definition match {
      case targetObject: ScObject => Some(targetObject)
      case _ => definition.baseCompanionModule.filterByType[ScObject] // todo ScalaPsiUtil.getCompanionModule / fakeCompanionModule
    }
  }

}

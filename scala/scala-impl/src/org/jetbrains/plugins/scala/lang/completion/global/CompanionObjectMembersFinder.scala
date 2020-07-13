package org.jetbrains.plugins.scala
package lang
package completion
package global

import com.intellij.psi.PsiElement
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

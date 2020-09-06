package org.jetbrains.plugins.scala.autoImport

import org.jetbrains.plugins.scala.extensions.{ObjectExt, OptionExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScThisType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

final case class GlobalImplicitInstance(override val owner: ScTypedDefinition,
                                        override val pathToOwner: String,
                                        override val member: ScMember)
  extends GlobalMember(owner, pathToOwner, member) {

  def toScalaResolveResult: ScalaResolveResult =
    new ScalaResolveResult(named, substitutor)
}

object GlobalImplicitInstance {
  def from(srr: ScalaResolveResult): Option[GlobalImplicitInstance] = {
    for {
      member <- srr.element.asOptionOfUnsafe[ScMember]
      obj <- containingObject(srr)
    } yield GlobalImplicitInstance(obj, obj.qualifiedName, member)
  }

  private def containingObject(srr: ScalaResolveResult): Option[ScObject] = {
    val ownerType = srr.implicitScopeObject.orElse {
      srr.element.containingClassOfNameContext
        .filterByType[ScTemplateDefinition]
        .map(c => srr.substitutor(ScThisType(c)))
    }
    ownerType.flatMap(_.extractClass).filterByType[ScObject]
  }

}
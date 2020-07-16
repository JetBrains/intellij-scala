package org.jetbrains.plugins.scala.autoImport

import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult.containingObject

final class GlobalImplicitInstance(owner: ScTypedDefinition, pathToOwner: String, member: ScMember)
  extends GlobalMember(owner, pathToOwner, member) {

  def toScalaResolveResult: ScalaResolveResult =
    new ScalaResolveResult(named, substitutor)
}

object GlobalImplicitInstance {

  def apply(owner: ScTypedDefinition, pathToOwner: String, member: ScMember): GlobalImplicitInstance =
    new GlobalImplicitInstance(owner, pathToOwner, member)

  def apply(owner: ScObject, member: ScMember): GlobalImplicitInstance =
    new GlobalImplicitInstance(owner, owner.qualifiedName, member)

  def from(srr: ScalaResolveResult): Option[GlobalImplicitInstance] = {
    for {
      member <- srr.element.asOptionOfUnsafe[ScMember]
      obj <- containingObject(srr)
    } yield GlobalImplicitInstance(obj, member)
  }
}
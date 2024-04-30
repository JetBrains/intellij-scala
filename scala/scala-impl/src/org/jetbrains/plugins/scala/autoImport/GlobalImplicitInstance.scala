package org.jetbrains.plugins.scala.autoImport

import org.jetbrains.plugins.scala.extensions.{ObjectExt, OptionExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtension
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScThisType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

final case class GlobalImplicitInstance(override val owner: GlobalMemberOwner,
                                        override val pathToOwner: String,
                                        override val member: ScMember)
  extends GlobalMember(owner, pathToOwner, member) {

  def toScalaResolveResult: ScalaResolveResult =
    new ScalaResolveResult(named, substitutor)
}

object GlobalImplicitInstance {
  def from(srr: ScalaResolveResult): Option[GlobalImplicitInstance] = {
    val fromObject = for {
      member <- srr.element.asOptionOfUnsafe[ScMember]
      obj <- containingObject(srr)
    } yield GlobalImplicitInstance(GlobalMemberOwner.TypedDefinition(obj), obj.qualifiedName, member)

    def fromPackage = for {
      member <- srr.element.asOptionOfUnsafe[ScMember]
      pkg <- containingPackage(srr)
    } yield GlobalImplicitInstance(GlobalMemberOwner.Packaging(pkg), pkg.fullPackageName, member)

    fromObject.orElse(fromPackage)
  }

  private def containingObject(srr: ScalaResolveResult): Option[ScObject] = {
    val ownerType = srr.implicitScopeObject.orElse {
      srr.element.containingClassOfNameContext
        .filterByType[ScTemplateDefinition]
        .map(c => srr.substitutor(ScThisType(c)))
    }
    ownerType.flatMap(_.extractClass).filterByType[ScObject]
  }

  private def containingPackage(srr: ScalaResolveResult): Option[ScPackaging] =
    if (srr.isExtensionCall) {
      val extOwner = srr.exportedInExtension.orElse(
        srr.element.getContext.getContext.asOptionOf[ScExtension]
      )

      extOwner.flatMap(_.getContext.asOptionOf[ScPackaging])
    }
    else srr.element.getContext.asOptionOf[ScPackaging]

}
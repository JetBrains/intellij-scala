package org.jetbrains.plugins.scala.autoImport

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.extensions.{&&, Parent}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil.{findAllInheritorObjectsForOwner, findInheritorObjectsForOwner, findPackageForTopLevelMember}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGivenDefinition, ScMember}
import org.jetbrains.plugins.scala.lang.psi.stubs.index.StableValIndex
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors.withAllInheritors
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

abstract class GlobalMember[M <: ScMember](val owner: GlobalMemberOwner,
                                           val pathToOwner: String,
                                           val member: M) {

  def named: ScNamedElement = member match {
    case named: ScNamedElement => named
    case vs: ScValueOrVariable => vs.declaredElements.head
  }

  def substitutor: ScSubstitutor = owner.substitutor(member)

  def qualifiedName: String = pathToOwner + "." + named.name
}

object GlobalMember {

  private case class GlobalMemberImpl[M <: ScMember](override val owner: GlobalMemberOwner,
                                                     override val pathToOwner: String,
                                                     override val member: M)
    extends GlobalMember[M](owner, pathToOwner, member)

  def apply[M <: ScMember](owner: GlobalMemberOwner, pathToOwner: String, member: M): GlobalMember[M] =
    GlobalMemberImpl(owner, pathToOwner, member)

  def findGlobalMembers[M <: ScMember, GM <: GlobalMember[M]](member: M,
                                                              scope: GlobalSearchScope)
                                                             (constructor: (GlobalMemberOwner, String, M) => GM): Set[GM] = {
    val fromObjects =
      findInheritorObjectsForOwner(member)
        .map(o => constructor(GlobalMemberOwner.TypedDefinition(o), o.qualifiedName, member))

    if (fromObjects.nonEmpty) fromObjects
    else {
      val fromPackage = findPackageForTopLevelMember(member)
        .map(p => constructor(GlobalMemberOwner.Packaging(p), p.fullPackageName, member))
        .toSet

      val fromInheritors = for {
        clazz            <- withAllInheritors(member.containingClass)
        valueOfClassType <- StableValIndex.findValuesOfClassType(clazz, scope)
        named            <- valueOfClassType.declaredElements
        containingObject <- findAllInheritorObjectsForOwner(valueOfClassType)
      } yield constructor(GlobalMemberOwner.TypedDefinition(named), containingObject.qualifiedName + "." + named.name, member)

      val fromGivens = member match {
        case fn: ScFunction =>
          fn.extensionMethodOwner.toSet[ScExtension].collect {
            case Parent((_: ScTemplateBody) && Parent((_: ScExtendsBlock) && Parent(givenDef: ScGivenDefinition))) =>
              constructor(GlobalMemberOwner.GivenDefinition(givenDef), givenDef.qualifiedName, member)
          }
        case _ => Set.empty
      }

      fromPackage ++ fromInheritors ++ fromGivens
    }
  }
}

package org.jetbrains.plugins.scala.autoImport

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil.{findAllInheritorObjectsForOwner, findInheritorObjectsForOwner}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.hasStablePath
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScPackaging, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.stubs.index.StableValIndex
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors.withAllInheritors
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

/** TODO: owner could be a [[ScPackaging]] in Scala 3 */
abstract class GlobalMember[M <: ScMember](val owner: ScTypedDefinition,
                                           val pathToOwner: String,
                                           val member: M) {

  def named: ScNamedElement = member match {
    case named: ScNamedElement => named
    case vs: ScValueOrVariable => vs.declaredElements.head
  }

  def substitutor: ScSubstitutor = owner match {
    case o: ScObject if hasStablePath(o) => MixinNodes.asSeenFromSubstitutor(o, member.containingClass)
    case td: ScTypedDefinition =>
      val maybeSubstitutor =
        for {
          valType        <- td.`type`().toOption
          (clazz, subst) <- valType.extractClassType
        } yield MixinNodes.asSeenFromSubstitutor(clazz, member.containingClass).followed(subst)

      maybeSubstitutor.getOrElse(ScSubstitutor.empty)
  }

  def qualifiedName: String = pathToOwner + "." + named.name
}

object GlobalMember {

  private case class GlobalMemberImpl[M <: ScMember](override val owner: ScTypedDefinition,
                                                     override val pathToOwner: String,
                                                     override val member: M)
    extends GlobalMember[M](owner, pathToOwner, member)

  def apply[M <: ScMember](owner: ScTypedDefinition, pathToOwner: String, member: M): GlobalMember[M] =
    GlobalMemberImpl(owner, pathToOwner, member)

  def findGlobalMembers[M <: ScMember, GM <: GlobalMember[M]](member: M,
                                                              scope: GlobalSearchScope)
                                                             (constructor: (ScTypedDefinition, String, M) => GM): Set[GM] = {
    val fromObjects =
      findInheritorObjectsForOwner(member)
        .map(o => constructor(o, o.qualifiedName, member))

    if (fromObjects.nonEmpty) fromObjects
    else {
      for {
        clazz            <- withAllInheritors(member.containingClass, scope)
        valueOfClassType <- StableValIndex.findValuesOfClassType(clazz, scope)
        named            <- valueOfClassType.declaredElements
        containingObject <- findAllInheritorObjectsForOwner(valueOfClassType)
      } yield constructor(named, containingObject.qualifiedName + "." + named.name, member)
    }
  }
}

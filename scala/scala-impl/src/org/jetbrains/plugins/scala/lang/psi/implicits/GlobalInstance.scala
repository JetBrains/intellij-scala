package org.jetbrains.plugins.scala.lang.psi.implicits

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.hasStablePath
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

class GlobalInstance[M <: ScMember](val owner: ScTypedDefinition,
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

  def canEqual(other: GlobalInstance[_]): Boolean = other.getClass == getClass

  override def hashCode(): Int = qualifiedName.hashCode

  override def equals(obj: Any): Boolean = obj match {
    case g: GlobalInstance[_] if canEqual(g) => g.qualifiedName == qualifiedName
    case _ => false
  }

  override def toString: String =
    s"${getClass.getSimpleName}(" + qualifiedName + ")"
}

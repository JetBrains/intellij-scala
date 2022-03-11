package org.jetbrains.plugins.scala.autoImport

import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.hasStablePath
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScPackaging, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

sealed trait GlobalMemberOwner {
  def element: PsiElement

  def name: String

  def substitutor(member: ScMember): ScSubstitutor
}

object GlobalMemberOwner {
  def unapply(owner: GlobalMemberOwner): Option[PsiElement] = Some(owner.element)

  final case class Class(override val element: PsiClass) extends GlobalMemberOwner {
    override val name: String = element.getName

    override def substitutor(member: ScMember): ScSubstitutor = element match {
      case o: ScObject if hasStablePath(o) => MixinNodes.asSeenFromSubstitutor(o, member.containingClass)
      case td: ScTypedDefinition => typedDefSubstitutor(td, member)
      case _ => ScSubstitutor.empty
    }
  }

  final case class TypedDefinition(override val element: ScTypedDefinition) extends GlobalMemberOwner {
    override val name: String = element.name

    override def substitutor(member: ScMember): ScSubstitutor = typedDefSubstitutor(element, member)
  }

  final case class Packaging(override val element: ScPackaging) extends GlobalMemberOwner {
    override val name: String = element.packageName

    override def substitutor(member: ScMember): ScSubstitutor = ScSubstitutor.empty
  }

  private def typedDefSubstitutor(td: ScTypedDefinition, member: ScMember): ScSubstitutor = {
    val maybeSubstitutor =
      for {
        valType        <- td.`type`().toOption
        (clazz, subst) <- valType.extractClassType
      } yield MixinNodes.asSeenFromSubstitutor(clazz, member.containingClass).followed(subst)

    maybeSubstitutor.getOrElse(ScSubstitutor.empty)
  }
}

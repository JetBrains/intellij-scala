package org.jetbrains.plugins.scala.lang
package psi
package implicits

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ImplicitInstanceIndex
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.util.CommonQualifiedNames._

case class GlobalImplicitInstance(containingObject: ScObject, member: ScMember) {

  def named: ScNamedElement = member match {
    case named: ScNamedElement => named
    case vs: ScValueOrVariable => vs.declaredElements.head
  }

  def possiblyUndefinedType: ScType = {
    val substitutor = MixinNodes.asSeenFromSubstitutor(containingObject, member.containingClass)

    val (maybeType, fullSubstitutor) = member match {
      case f: ScFunction =>
        (f.returnType, substitutor.followed(ScalaPsiUtil.undefineMethodTypeParams(f)))
      case t: Typeable =>
        (t.`type`(), substitutor)
    }

    fullSubstitutor(maybeType.getOrNothing)
  }

  def qualifiedName: String = containingObject.qualifiedName + "." + named.name

  override def hashCode(): Int = qualifiedName.hashCode

  override def equals(obj: Any): Boolean = obj match {
    case g: GlobalImplicitInstance => g.qualifiedName == qualifiedName
    case _ => false
  }

  override def toString: String = "GlobalImplicitInstance(" + qualifiedName + ")"
}

object GlobalImplicitInstance {

  private[this] def allCandidates(clazz: PsiClass, scope: GlobalSearchScope)
                                 (implicit project: Project): Set[ScMember] = for {
    psiClass <- ScalaInheritors.withStableScalaInheritors(clazz)

    qualifiedName = psiClass.qualifiedName
    if qualifiedName != AnyRefFqn &&
      qualifiedName != AnyFqn &&
      qualifiedName != JavaObjectFqn

    candidate <- ImplicitInstanceIndex.forClassFqn(qualifiedName, scope)
  } yield candidate

  private[this] def globalInstances(member: ScMember): Set[GlobalImplicitInstance] =
    for {
      containingClass <- Option(member.containingClass).toSet[ScTemplateDefinition]
      objectToImport  <- ScalaInheritors.findInheritorObjects(containingClass)
    } yield GlobalImplicitInstance(objectToImport, member)

  def compatibleInstances(`type`: ScType, elementScope: ElementScope): Set[GlobalImplicitInstance] = {
    implicit val ElementScope(project, scope) = elementScope

    for {
      clazz <- `type`.extractClass.toSet[PsiClass]
      candidate <- allCandidates(clazz, scope)

      global <- globalInstances(candidate)
      if global.possiblyUndefinedType.conforms(`type`)
    } yield global
  }

}
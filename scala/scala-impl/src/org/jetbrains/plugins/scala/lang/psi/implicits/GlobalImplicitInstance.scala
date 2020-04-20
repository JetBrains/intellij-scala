package org.jetbrains.plugins.scala
package lang
package psi
package implicits

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ImplicitInstanceIndex
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors.{findInheritorObjectsForContainer, withStableScalaInheritors}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.util.CommonQualifiedNames._

final case class GlobalImplicitInstance(containingObject: ScObject, member: ScMember) {

  def named: ScNamedElement = member match {
    case named: ScNamedElement => named
    case vs: ScValueOrVariable => vs.declaredElements.head
  }

  private def possiblyUndefinedType: ScType = {
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

  def compatibleInstances(`type`: ScType,
                          scope: GlobalSearchScope)
                         (implicit project: Project): Set[GlobalImplicitInstance] = for {
    clazz <- `type`.extractClass.toSet[PsiClass]

    qualifiedName <- withStableScalaInheritors(clazz)
    if !isRootClass(qualifiedName)

    candidateMember <- ImplicitInstanceIndex.forClassFqn(qualifiedName, scope)
    objectToImport <- findInheritorObjectsForContainer(candidateMember)

    global = GlobalImplicitInstance(objectToImport, candidateMember)
    if global.possiblyUndefinedType.conforms(`type`)
  } yield global

  private[this] def isRootClass(qualifiedName: String) = qualifiedName match {
    case AnyRefFqn | AnyFqn | JavaObjectFqn => true
    case _ => false
  }
}
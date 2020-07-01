package org.jetbrains.plugins.scala
package lang
package psi
package implicits

import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil.findInheritorObjectsForOwner
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector.TypeDoesntConformResult
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ImplicitInstanceIndex
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors.withStableInheritors
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, WrongTypeParameterInferred}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult.containingObject
import org.jetbrains.plugins.scala.util.CommonQualifiedNames._

final case class GlobalImplicitInstance(containingObject: ScObject, member: ScMember) {

  def named: ScNamedElement = member match {
    case named: ScNamedElement => named
    case vs: ScValueOrVariable => vs.declaredElements.head
  }

  def toScalaResolveResult: ScalaResolveResult =
    new ScalaResolveResult(named, MixinNodes.asSeenFromSubstitutor(containingObject, member.containingClass))

  def qualifiedName: String = containingObject.qualifiedName + "." + named.name

  override def hashCode(): Int = qualifiedName.hashCode

  override def equals(obj: Any): Boolean = obj match {
    case g: GlobalImplicitInstance => g.qualifiedName == qualifiedName
    case _ => false
  }

  override def toString: String = "GlobalImplicitInstance(" + qualifiedName + ")"
}

object GlobalImplicitInstance {

  def from(srr: ScalaResolveResult): Option[GlobalImplicitInstance] = {
    for {
      member <- srr.element.asOptionOfUnsafe[ScMember]
      obj <- containingObject(srr)
    } yield GlobalImplicitInstance(obj, member)
  }

  def compatibleInstances(`type`: ScType,
                          scope: GlobalSearchScope,
                          place: ImplicitArgumentsOwner): Set[GlobalImplicitInstance] = {
    val collector = new ImplicitCollector(place, `type`, `type`, None, false, fullInfo = true)
    for {
      clazz <- `type`.extractClass.toSet[PsiClass]

      qualifiedName <- withStableInheritors(clazz)
      if !isRootClass(qualifiedName)

      candidateMember <- ImplicitInstanceIndex.forClassFqn(qualifiedName, scope)(place.getProject)
      objectToImport <- findInheritorObjectsForOwner(candidateMember)

      global = GlobalImplicitInstance(objectToImport, candidateMember)
      if checkCompatible(global, collector)
    } yield global
  }

  private[this] def isRootClass(qualifiedName: String) = qualifiedName match {
    case AnyRefFqn | AnyFqn | JavaObjectFqn => true
    case _ => false
  }

  private def checkCompatible(global: GlobalImplicitInstance, collector: ImplicitCollector): Boolean = {
    val srr = global.toScalaResolveResult
    collector.checkCompatible(srr, withLocalTypeInference = false)
      .orElse(collector.checkCompatible(srr, withLocalTypeInference = true))
      .exists(isCompatible)
  }

  private def isCompatible(srr: ScalaResolveResult): Boolean = {
    !srr.problems.contains(WrongTypeParameterInferred) && srr.implicitReason != TypeDoesntConformResult
  }
}
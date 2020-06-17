package org.jetbrains.plugins.scala
package lang
package psi
package implicits

import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ImplicitInstanceIndex
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors.findInheritorObjectsForOwner
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors.withStableScalaInheritors
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.util.CommonQualifiedNames._
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.extensions.OptionExt
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector.TypeDoesntConformResult
import org.jetbrains.plugins.scala.lang.psi.types.WrongTypeParameterInferred
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScThisType

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
      member <- srr.element.asOptionOf[ScMember]
      obj <- containingObject(srr)
    } yield GlobalImplicitInstance(obj, member)
  }

  def compatibleInstances(`type`: ScType,
                          scope: GlobalSearchScope,
                          place: ImplicitArgumentsOwner): Set[GlobalImplicitInstance] = {
    val collector = new ImplicitCollector(place, `type`, `type`, None, false, fullInfo = true)
    for {
      clazz <- `type`.extractClass.toSet[PsiClass]

      qualifiedName <- withStableScalaInheritors(clazz)
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

  private def containingObject(srr: ScalaResolveResult): Option[ScObject] = {
    val ownerType = srr.implicitScopeObject.orElse {
      srr.element.containingClassOfNameContext
        .filterByType[ScTemplateDefinition]
        .map(c => srr.substitutor(ScThisType(c)))
    }
    ownerType.flatMap(_.extractClass).filterByType[ScObject]
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
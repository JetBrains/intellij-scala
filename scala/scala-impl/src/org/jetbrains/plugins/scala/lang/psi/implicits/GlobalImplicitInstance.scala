package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ImplicitInstanceIndex
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}

case class GlobalImplicitInstance(containingObject: ScObject, member: ScMember) {

  def named: ScNamedElement = member match {
    case named: ScNamedElement => named
    case vs: ScValueOrVariable => vs.declaredElements.head
  }

  def possiblyUndefinedType: ScType = {
    val fullSubstitor = member match {
      case f: ScFunction => substitutor.followed(ScalaPsiUtil.undefineMethodTypeParams(f))
      case _ => substitutor
    }
    val tpe = member match {
      case f: ScFunction => f.returnType.getOrNothing
      case t: Typeable   => t.`type`().getOrNothing
    }
    fullSubstitor(tpe)
  }

  def qualifiedName: String = containingObject.qualifiedName + "." + named.name

  override def hashCode(): Int = qualifiedName.hashCode

  override def equals(obj: Any): Boolean = obj match {
    case g: GlobalImplicitInstance => g.qualifiedName == qualifiedName
    case _ => false
  }

  private def substitutor =
    MixinNodes.asSeenFromSubstitutor(containingObject, member.containingClass)

  override def toString: String = s"GlobalImplicitInstance($qualifiedName)"
}

object GlobalImplicitInstance {
  //these types are too broad to search implicits for them
  private val ignoredClasses: Set[String] = Set("scala.AnyRef", "scala.Any", "java.lang.Object")

  def exactClassCandidates(clazz: PsiClass, scope: GlobalSearchScope, project: Project): Seq[ScMember] = {
    val qName = clazz.qualifiedName

    if (ignoredClasses.contains(qName))
      Seq.empty
    else
      ImplicitInstanceIndex.forClassFqn(qName, scope, project)
  }

  def allCandidates(clazz: PsiClass, scope: GlobalSearchScope): Set[ScMember] = {
    val project = clazz.getProject

    for {
      psiClass  <- ScalaInheritors.withStableScalaInheritors(clazz)
      candidate <- exactClassCandidates(psiClass, scope, project)
    } yield {
      candidate
    }
  }

  def globalInstances(member: ScMember): Set[GlobalImplicitInstance] =
    for {
      containingClass <- member.containingClass.toOption.toSet[ScTemplateDefinition]
      objectToImport  <- ScalaPsiManager.instance(member).inheritorOrThisObjects(containingClass)
    } yield {
      GlobalImplicitInstance(objectToImport, member)
    }

  def compatibleInstances(tp: ScType, elementScope: ElementScope): Set[GlobalImplicitInstance] = {
    for {
      clazz     <- tp.extractClass.toSet[PsiClass]
      candidate <- allCandidates(clazz, elementScope.scope)

      global    <- globalInstances(candidate)

      if global.possiblyUndefinedType.conforms(tp)

    } yield {
      global
    }
  }

}
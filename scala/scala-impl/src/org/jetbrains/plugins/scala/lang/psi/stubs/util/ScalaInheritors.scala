package org.jetbrains.plugins.scala.lang.psi.stubs.util

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope, SearchScope}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedInUserData}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.finder.ScalaFilterScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.{ALIASED_CLASS_NAME_KEY, ALIASED_IMPORT_KEY, SUPER_CLASS_NAME_KEY, StubIndexKeyExt}
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScType, ScTypeExt}
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.reflect.ClassTag

object ScalaInheritors {
  def directSupersNames(extBlock: ScExtendsBlock): ArraySeq[String] = ScalaDirectSupers.directSupersNames(extBlock)

  def directInheritorCandidates(clazz: PsiClass, scope: SearchScope): Seq[ScTemplateDefinition] =
    scope match {
      case scope: GlobalSearchScope => directInheritorCandidates(clazz, scope)
      case scope: LocalSearchScope  => directInheritorCandidates(clazz, scope)
      case _                        => Seq()
    }

  def directInheritorCandidates(clazz: PsiClass, scope: GlobalSearchScope): Seq[ScTemplateDefinition] = {
    implicit val project: Project = clazz.getProject

    val name: String = clazz.name
    if (name == null || clazz.isEffectivelyFinal)
      return Seq.empty

    val inheritorsBuilder = ArraySeq.newBuilder[ScTemplateDefinition]

    val possibleNames: List[String] = {
      val typeAliases =
        ALIASED_CLASS_NAME_KEY.elements(name, scope).map(_.name)
          .toList
      val importAliases =
        ALIASED_IMPORT_KEY.elements(name, scope).flatMap(_.aliasName).toList

      name :: typeAliases ::: importAliases
    }

    possibleNames.foreach { superName =>
      val extendsBlockIterable = SUPER_CLASS_NAME_KEY.elements(superName, scope)
      val extendsBlocks = extendsBlockIterable.iterator

      while (extendsBlocks.hasNext) {
        ProgressManager.checkCanceled()

        val extendsBlock = extendsBlocks.next()
        extendsBlock.getParent match {
          case tp: ScTemplateDefinition if tp.isInheritor(clazz, false) =>
            inheritorsBuilder += tp
          case _ =>
        }
      }
    }

    inheritorsBuilder.result()
  }

  def directInheritorCandidates(clazz: PsiClass, localScope: LocalSearchScope): Seq[ScTemplateDefinition] = {
    val name: String = clazz.name
    if (name == null || clazz.isEffectivelyFinal)
      return Seq.empty

    val scopeElements = localScope.getScope.toSeq
    scopeElements.flatMap { element =>
      element.elements
        .filterByType[ScTemplateDefinition]
        .filter(_.isInheritor(clazz, false))
    }
  }

  def getSelfTypeInheritors(clazz: PsiClass): Seq[ScTemplateDefinition] = {
    def selfTypeInheritorsInner(): Seq[ScTemplateDefinition] = cachedInUserData("selfTypeInheritorsInner", clazz, BlockModificationTracker(clazz)) {
      if (clazz.name == null) {
        return Seq.empty
      }

      val inheritorsBuilder = ArraySeq.newBuilder[ScTemplateDefinition]

      implicit val project: Project = clazz.getProject
      val resolveScope = clazz.resolveScope

      def processClass(inheritedClazz: PsiClass): Unit = {
        val name = inheritedClazz.name
        if (name == null) {
          return
        }

        def checkTp(tp: ScType): Boolean =
          tp match {
            case c: ScCompoundType =>
              c.components.exists(checkTp)
            case _ =>
              tp.extractClass match {
                case Some(otherClazz) if ScEquivalenceUtil.areClassesEquivalent(inheritedClazz, otherClazz) =>
                  true
                case _ => false
              }
          }

        inReadAction {
          import ScalaIndexKeys._
          for {
            selfTypeElement <- SELF_TYPE_CLASS_NAME_KEY.elements(name, resolveScope)
            typeElement <- selfTypeElement.typeElement
            tp <- typeElement.`type`().toOption
            if checkTp(tp)
            clazz = PsiTreeUtil.getContextOfType(selfTypeElement, classOf[ScTemplateDefinition])
          } if (clazz != null) inheritorsBuilder += clazz
        }
      }

      processClass(clazz)
      ClassInheritorsSearch.search(clazz, ScalaFilterScope(resolveScope), true).forEach(new Processor[PsiClass] {
        override def process(t: PsiClass): Boolean = {
          processClass(t)
          true
        }
      })
      inheritorsBuilder.result()
    }

    inReadAction {
      if (clazz.isEffectivelyFinal) Seq.empty
      else selfTypeInheritorsInner()
    }
  }

  private def withInheritors[T <: PsiClass : ClassTag](clazz: PsiClass)
                                                      (predicate: T => Boolean): Set[T] = {
    val builder = Set.newBuilder[T]
    val visited = mutable.Set.empty[PsiClass]

    def inner(clazz: PsiClass): Unit = {
      if (!visited(clazz)) {
        visited += clazz

        clazz match {
          case t: T if predicate(t) => builder += t
          case _ =>
        }

        clazz match {
          case td: ScTypeDefinition if !td.isEffectivelyFinal =>
            val directInheritors = directInheritorCandidates(clazz, clazz.resolveScope)
            directInheritors.foreach(inner)

          //todo collect inheritors of java classes
          case _ =>
        }
      }
    }

    inner(clazz)
    builder.result()
  }

  private def withStableInheritors[T <: PsiClass : ClassTag](clazz: PsiClass): Set[T] =
    withInheritors(clazz)(ScalaPsiUtil.hasStablePath)

  def withStableInheritorsNames(clazz: PsiClass): Set[String] =
    withStableInheritors[PsiClass](clazz).map(_.qualifiedName)

  def allInheritorObjects(clazz: ScTemplateDefinition): Set[ScObject] =
    withStableInheritors[ScObject](clazz)

  def withAllInheritors(clazz: PsiClass): Set[PsiClass] =
    withInheritors(clazz)(Function.const(true))
}

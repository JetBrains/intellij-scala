package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package util

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, CachesUtil}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.finder.ScalaFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScType, ScTypeExt}
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.10.2008
 */

object ScalaInheritors {
  def directInheritorCandidates(clazz: PsiClass, scope: GlobalSearchScope): Seq[ScTemplateDefinition] = {
    val name: String = clazz.name
    if (name == null || clazz.isEffectivelyFinal) return Seq.empty

    val inheritors = new ArrayBuffer[ScTemplateDefinition]

    import ScalaIndexKeys._
    val extendsBlocks = SUPER_CLASS_NAME_KEY.elements(name, scope, classOf[ScExtendsBlock])(clazz.getProject)
      .iterator

    while (extendsBlocks.hasNext) {
      val extendsBlock = extendsBlocks.next
      extendsBlock.getParent match {
        case tp: ScTemplateDefinition => inheritors += tp
        case _ =>
      }
    }
    inheritors
  }

  def getSelfTypeInheritors(clazz: PsiClass): Seq[ScTemplateDefinition] = {
    @CachedInUserData(clazz, BlockModificationTracker(clazz))
    def selfTypeInheritorsInner(): Seq[ScTemplateDefinition] = {
      if (clazz.name == null) {
        return Seq.empty
      }

      val inheritors = new ArrayBuffer[ScTemplateDefinition]

      implicit val project: Project = clazz.getProject
      val resolveScope = clazz.resolveScope

      def processClass(inheritedClazz: PsiClass) {
        val name = inheritedClazz.name
        if (name == null) {
          return
        }

        def checkTp(tp: ScType): Boolean = {
          tp match {
            case c: ScCompoundType =>
              c.components.exists(checkTp)
            case _ =>
              tp.extractClass match {
                case Some(otherClazz) =>
                  if (ScEquivalenceUtil.areClassesEquivalent(inheritedClazz, otherClazz)) return true
                case _ =>
              }
          }
          false
        }
        inReadAction {
          import ScalaIndexKeys._
          for {
            selfTypeElement <- SELF_TYPE_CLASS_NAME_KEY.elements(name, resolveScope, classOf[ScSelfTypeElement])
            typeElement <- selfTypeElement.typeElement
            tp <- typeElement.`type`().toOption
            if checkTp(tp)
            clazz = PsiTreeUtil.getContextOfType(selfTypeElement, classOf[ScTemplateDefinition])
          } if (clazz != null) inheritors += clazz
        }
      }
      processClass(clazz)
      ClassInheritorsSearch.search(clazz, ScalaFilterScope(project, resolveScope), true).forEach(new Processor[PsiClass] {
        def process(t: PsiClass): Boolean = {
          processClass(t)
          true
        }
      })
      inheritors.toVector
    }

    if (clazz.isEffectivelyFinal) Seq.empty
    else selfTypeInheritorsInner()
  }

  def withStableScalaInheritors(clazz: PsiClass): Set[ScTypeDefinition] =
    collectStableInheritors[ScTypeDefinition](clazz)

  private def collectStableInheritors[T <: ScTypeDefinition : ClassTag](clazz: PsiClass,
                                                                        visited: Set[PsiClass] = Set.empty,
                                                                        buffer: ArrayBuffer[T] = ArrayBuffer.empty[T]): Set[T] = {
    if (!visited(clazz)) {

      clazz match {
        case t: T if ScalaPsiUtil.hasStablePath(t) => buffer += t
        case _ =>
      }

      clazz match {
        case td: ScTypeDefinition if !td.isEffectivelyFinal =>
          val directInheritors = directInheritorCandidates(clazz, clazz.resolveScope).filter(_.isInheritor(td, deep = false))
          directInheritors
            .foreach(collectStableInheritors(_, visited + clazz, buffer))
        case _ =>
      }
    }

    buffer.toSet
  }

  private def allInheritorObjects(clazz: ScTemplateDefinition): Set[ScObject] =
    collectStableInheritors[ScObject](clazz)

  //find objects which may be used to import members of `clazz`
  //if `clazz` is not generic, members in all objects are the same, so we return one that have less methods as it is more specific
  def findInheritorObjects(clazz: ScTemplateDefinition): Set[ScObject] = {
    val allObjects = allInheritorObjects(clazz)

    def nameCount(obj: ScObject): Int = TypeDefinitionMembers.getSignatures(obj).nameCount

    if (clazz.hasTypeParameters || allObjects.isEmpty) allObjects
    else {
      Set(allObjects.minBy(nameCount))
    }
  }
}

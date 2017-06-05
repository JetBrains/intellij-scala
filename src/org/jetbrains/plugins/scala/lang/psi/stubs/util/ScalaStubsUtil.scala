package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package util


import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.finder.ScalaSourceFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.SUPER_CLASS_NAME_KEY
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScType, ScTypeExt}
import org.jetbrains.plugins.scala.macroAnnotations.CachedInsidePsiElement
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.collection.mutable.ArrayBuffer

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.10.2008
 */

object ScalaStubsUtil {
  def getClassInheritors(clazz: PsiClass, scope: GlobalSearchScope): Seq[ScTemplateDefinition] = {
    val name: String = clazz.name
    if (name == null || clazz.isEffectivelyFinal) return Seq.empty

    val inheritors = new ArrayBuffer[ScTemplateDefinition]
    val scalaScope = new ScalaSourceFilterScope(scope, clazz.getProject)

    val extendsBlocks =
      StubIndex.getElements(SUPER_CLASS_NAME_KEY, name, clazz.getProject, scalaScope, classOf[ScExtendsBlock]).iterator

    while (extendsBlocks.hasNext) {
      val extendsBlock = extendsBlocks.next
      extendsBlock.greenStub match {
        case Some(stub: ScTemplateDefinitionStub) => inheritors += stub.getPsi
        case _ =>
          extendsBlock.getParent match {
            case tp: ScTemplateDefinition => inheritors += tp
            case _ =>
          }
      }
    }
    inheritors.toVector
  }

  def getSelfTypeInheritors(clazz: PsiClass): Seq[ScTemplateDefinition] = {
    @CachedInsidePsiElement(clazz, CachesUtil.enclosingModificationOwner(clazz))
    def selfTypeInheritorsInner(): Seq[ScTemplateDefinition] = {
      val scope = clazz.getResolveScope
      val inheritors = new ArrayBuffer[ScTemplateDefinition]
      val project = clazz.getProject
      val name = clazz.name
      if (name == null) return Seq.empty

      def processClass(inheritedClazz: PsiClass) {
        def checkTp(tp: ScType): Boolean = {
          tp match {
            case c: ScCompoundType =>
              c.components.exists(checkTp)
            case _ =>
              tp.extractClass match {
                case Some(otherClazz) =>
                  if (ScEquivalenceUtil.areClassesEquivalent(clazz, otherClazz)) return true
                case _ =>
              }
          }
          false
        }
        inReadAction {
          val iterator: java.util.Iterator[ScSelfTypeElement] =
            StubIndex.getElements(ScalaIndexKeys.SELF_TYPE_CLASS_NAME_KEY, name, project, scope, classOf[ScSelfTypeElement]).iterator
          while (iterator.hasNext) {
            val selfTypeElement = iterator.next
            selfTypeElement.typeElement match {
              case Some(typeElement) =>
                typeElement.getType(TypingContext.empty) match {
                  case Success(tp, _) =>
                    if (checkTp(tp)) {
                      val clazz = PsiTreeUtil.getContextOfType(selfTypeElement, classOf[ScTemplateDefinition])
                      if (clazz != null) inheritors += clazz
                    }
                  case _ =>
                }
              case _ =>
            }
          }
        }
      }
      processClass(clazz)
      ClassInheritorsSearch.search(clazz, scope, true).forEach(new Processor[PsiClass] {
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

  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaStubsUtil")
}

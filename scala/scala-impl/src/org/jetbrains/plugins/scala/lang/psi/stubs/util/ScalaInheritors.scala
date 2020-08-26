package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package util

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.searches.{ClassInheritorsSearch, ReferencesSearch}
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope, SearchScope}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.finder.ScalaFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScInfixTypeElement, ScParameterizedTypeElement, ScParenthesisedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScType, ScTypeExt}
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag

object ScalaInheritors {
  private val defaultParents   : Array[String] = Array("Object")
  private val caseClassDefaults: Array[String] = defaultParents :+ "Product" :+ "Serializable"

  def directSupersNames(extBlock: ScExtendsBlock): Array[String] = {
    def default = if (extBlock.isUnderCaseClass) caseClassDefaults else defaultParents

    collectForDirectSuperReferences(extBlock, _.refName) ++ default
  }

  private def directSuperReferenceTexts(extendsBlock: ScExtendsBlock): Array[String] = {
    collectForDirectSuperReferences(extendsBlock, _.getText)
  }

  private def collectForDirectSuperReferences(extBlock: ScExtendsBlock,
                                                 function: ScStableCodeReference => String): Array[String] = {
    @tailrec
    def extractReference(te: ScTypeElement): Option[ScStableCodeReference] = {
      te match {
        case simpleType: ScSimpleTypeElement => simpleType.reference
        case infixType: ScInfixTypeElement => Option(infixType.operation)
        case x: ScParameterizedTypeElement => extractReference(x.typeElement)
        case x: ScParenthesisedTypeElement =>
          x.innerElement match {
            case Some(e) => extractReference(e)
            case _ => None
          }
        case _ => None
      }
    }

    extBlock.templateParents match {
      case None => Array.empty
      case Some(parents) =>
        val parentElements = parents.typeElements.iterator
        val result = new ArrayBuffer[String]()
        while (parentElements.hasNext) {
          extractReference(parentElements.next()) match {
            case Some(value) =>
              result += function(value)
            case _ =>
          }
        }
        result.toArray
    }
  }

  def directInheritorCandidates(clazz: PsiClass, scope: SearchScope): collection.Seq[ScTemplateDefinition] =
    scope match {
      case scope: GlobalSearchScope => directInheritorCandidates(clazz, scope)
      case scope: LocalSearchScope  => directInheritorCandidates(clazz, scope)
      case _                        => Seq()
    }

  def directInheritorCandidates(clazz: PsiClass, scope: GlobalSearchScope): collection.Seq[ScTemplateDefinition] = {
    val name: String = clazz.name
    val qName = clazz.qualifiedNameOpt.getOrElse(name)
    if (name == null || clazz.isEffectivelyFinal) return Seq.empty

    val inheritors = new ArrayBuffer[ScTemplateDefinition]

    import ScalaIndexKeys._
    val extendsBlockIterable = SUPER_CLASS_NAME_KEY.elements(name, scope)(clazz.getProject)
    val extendsBlocks = extendsBlockIterable.iterator

    while (extendsBlocks.hasNext) {
      val extendsBlock = extendsBlocks.next
      extendsBlock.getParent match {
        case tp: ScTemplateDefinition =>
          // simple names are stored in index, but in decompiled files they are qualified
          val superReferenceTexts =
            directSuperReferenceTexts(extendsBlock)
              .map(_.stripPrefix("_root_.").stripPrefix("super."))

          if (superReferenceTexts.exists(qName.endsWith)) {
            inheritors += tp
          }
        case _ =>
      }
    }
    inheritors
  }

  def directInheritorCandidates(clazz: PsiClass, localScope: LocalSearchScope): collection.Seq[ScTemplateDefinition] = {
    val name: String = clazz.name
    if (name == null || clazz.isEffectivelyFinal) return Seq.empty

    val inheritors = new ArrayBuffer[ScTemplateDefinition]

    val references = ReferencesSearch.search(clazz, localScope).findAll().asScala
    val extendsBlocksIterable = references.collect {
      case Parent(Parent(Parent(Parent(extendsBlock: ScExtendsBlock)))) => extendsBlock
    }
    val extendsBlocks = extendsBlocksIterable.iterator
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

      def processClass(inheritedClazz: PsiClass): Unit = {
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
            selfTypeElement <- SELF_TYPE_CLASS_NAME_KEY.elements(name, resolveScope)
            typeElement <- selfTypeElement.typeElement
            tp <- typeElement.`type`().toOption
            if checkTp(tp)
            clazz = PsiTreeUtil.getContextOfType(selfTypeElement, classOf[ScTemplateDefinition])
          } if (clazz != null) inheritors += clazz
        }
      }
      processClass(clazz)
      ClassInheritorsSearch.search(clazz, ScalaFilterScope(resolveScope), true).forEach(new Processor[PsiClass] {
        override def process(t: PsiClass): Boolean = {
          processClass(t)
          true
        }
      })
      inheritors.toVector
    }

    if (clazz.isEffectivelyFinal) Seq.empty
    else selfTypeInheritorsInner()
  }

  private def withInheritors[T <: PsiClass : ClassTag](clazz: PsiClass,
                                                       scope: GlobalSearchScope,
                                                       visited: Set[PsiClass] = Set.empty,
                                                       buffer: ArrayBuffer[T] = ArrayBuffer.empty[T])
                                                      (predicate: T => Boolean): Set[T] = {
    if (!visited(clazz)) {

      clazz match {
        case t: T if predicate(t) => buffer += t
        case _ =>
      }

      clazz match {
        case td: ScTypeDefinition if !td.isEffectivelyFinal =>
          val directInheritors = directInheritorCandidates(clazz, clazz.resolveScope).filter(_.isInheritor(td, false))
          directInheritors
            .foreach(withInheritors(_, scope, visited + clazz, buffer)(predicate))

        //todo collect inheritors of java classes
        case _ =>
      }
    }

    buffer.toSet
  }

  private def withStableInheritors[T <: PsiClass : ClassTag](clazz: PsiClass, scope: GlobalSearchScope): Set[T] =
    withInheritors(clazz, scope)(ScalaPsiUtil.hasStablePath)

  def withStableInheritorsNames[T <: PsiClass : ClassTag](clazz: PsiClass, scope: GlobalSearchScope): Set[String] =
    withStableInheritors[PsiClass](clazz, scope).map(_.qualifiedName)

  def allInheritorObjects(clazz: ScTemplateDefinition, scope: GlobalSearchScope): Set[ScObject] =
    withStableInheritors[ScObject](clazz, scope)

  def withAllInheritors(clazz: PsiClass, scope: GlobalSearchScope): Set[PsiClass] =
    withInheritors(clazz, scope)(Function.const(true))
}

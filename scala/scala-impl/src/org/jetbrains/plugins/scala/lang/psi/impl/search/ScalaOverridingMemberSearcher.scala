package org.jetbrains.plugins.scala.lang.psi.impl.search

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.OverridingMethodsSearch.SearchParameters
import com.intellij.psi.search.searches.{ClassInheritorsSearch, OverridingMethodsSearch}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtensionBody, ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors

import scala.collection.mutable

/**
 * This class is required for Ctrl+Alt+B action for cases when not PsiMethod overrides not PsiMethod (one of two cases)
 */
class MethodImplementationsSearch extends QueryExecutor[PsiElement, PsiElement] {
  override def execute(sourceElement: PsiElement, consumer: Processor[_ >: PsiElement]): Boolean = {
    sourceElement match {
      case namedElement: ScNamedElement =>
        for (implementation <- ScalaOverridingMemberSearcher.getOverridingMethods(namedElement)
             //to avoid duplicates with ScalaOverridingMemberSearcher
             if !namedElement.isInstanceOf[PsiMethod] || !implementation.is[PsiMethod]) {
          if (!consumer.process(implementation)) {
            return false
          }
        }
      case _ =>
    }
    true
  }
}

/**
 *  This class is required for Ctrl+Alt+B action for cases when PsiMethod overrides PsiMethod (no Wrappers!)
 *  That's why we need to stop processing, to avoid showing wrappers in Scala.
 */
class ScalaOverridingMemberSearcher extends QueryExecutor[PsiMethod, OverridingMethodsSearch.SearchParameters] {
  override def execute(queryParameters: SearchParameters, consumer: Processor[_ >: PsiMethod]): Boolean = {
    val method = queryParameters.getMethod
    method match {
      case namedElement: ScNamedElement =>
        for (implementation <- ScalaOverridingMemberSearcher.getOverridingMethods(namedElement)
             if implementation.is[PsiMethod]) {
          if (!consumer.process(implementation.asInstanceOf[PsiMethod])) {
            return false
          }
        }
        false //do not process JavaOverridingMemberSearcher
      case _ => true
    }
  }
}

object ScalaOverridingMemberSearcher {
  def getOverridingMethods(method: ScNamedElement): Array[PsiNamedElement] = inReadAction {
    ScalaOverridingMemberSearcher.search(method)
  }

  def search(
    member: PsiNamedElement,
    scopeOption: Option[SearchScope] = None,
    deep: Boolean = true,
    withSelfType: Boolean = false
  ): Array[PsiNamedElement] = {
    val scope = scopeOption.getOrElse(inReadAction(member.getUseScope))

    ProgressManager.checkCanceled()

    if (!isOverridingMemberSearchApplicable(member)) {
      return Array.empty
    }

    val parentClass = member match {
      case m: PsiMethod       => m.containingClass
      case x: PsiNamedElement => PsiTreeUtil.getParentOfType(x, classOf[ScTemplateDefinition])
    }

    ProgressManager.checkCanceled()

    // e.g. if `member` is function inside Scala3 `given`
    if (parentClass == null)
      return Array.empty

    if (inReadAction(parentClass.isEffectivelyFinal))
      return Array.empty

    val inheritors: Array[PsiClass] = inReadAction {
      ClassInheritorsSearch.search(parentClass, scope, true).toArray(PsiClass.EMPTY_ARRAY)
    }

    val buffer = mutable.Set.empty[PsiNamedElement]

    def process(inheritor: PsiClass): Boolean = {
      inReadAction {
        processImpl(inheritor, member, deep, withSelfType, buffer)
      }
    }

    var break = false
    for (inheritor <- inheritors if !break) {
      ProgressManager.checkCanceled()
      break = !process(inheritor)
    }

    if (withSelfType) {
      val inheritors: Seq[ScTemplateDefinition] = ScalaInheritors.getSelfTypeInheritors(parentClass)
      break = false
      for (inheritor <- inheritors if !break) {
        ProgressManager.checkCanceled()
        break = !process(inheritor)
      }
    }

    buffer.toArray
  }

  private def isOverridingMemberSearchApplicable(member: PsiNamedElement): Boolean = {
    def inTemplateBodyOrEarlyDef(element: PsiElement): Boolean = {
      val parent = inReadAction(element.getParent)
      parent match {
        case _: ScTemplateBody | _: ScEarlyDefinitions => true
        case _: ScExtensionBody => true
        case _ => false
      }
    }

    member match {
      case _: ScFunction | _: ScTypeAlias =>
        inTemplateBodyOrEarlyDef(member)
      case td: ScTypeDefinition if !td.isObject =>
        inTemplateBodyOrEarlyDef(member)
      case cp: ScClassParameter if cp.isClassMember =>
        true
      case x: PsiNamedElement =>
        val nameContext = x.nameContext
        nameContext != null && inTemplateBodyOrEarlyDef(nameContext)
      case _ =>
        false
    }
  }

  private def processImpl(
    inheritor: PsiClass,
    originalMember: PsiNamedElement,
    deep: Boolean,
    withSelfType: Boolean,
    resultBuffer: mutable.Set[PsiNamedElement]
  ): Boolean = {
    def collectInheritorsOfType(name: String): Boolean = {
      inheritor match {
        case inheritor: ScTypeDefinition =>
          for (alias <- inheritor.aliases if name == alias.name) {
            resultBuffer += alias
            if (!deep)
              return false
          }
          for (td <- inheritor.typeDefinitions if !td.isObject && name == td.name) {
            resultBuffer += td
            if (!deep)
              return false
          }
        case _ =>
      }
      true
    }

    originalMember match {
      case alias: ScTypeAlias =>
        val continue = collectInheritorsOfType(alias.name)
        if (!continue)
          return false
      case td: ScTypeDefinition if !td.isObject =>
        val continue = collectInheritorsOfType(td.name)
        if (!continue)
          return false
      case _: PsiNamedElement =>
        val signatures =
          if (withSelfType) TypeDefinitionMembers.getSelfTypeSignatures(inheritor)
          else TypeDefinitionMembers.getSignatures(inheritor)
        val signsIterator = signatures.forName(originalMember.name).nodesIterator
        while (signsIterator.hasNext) {
          val node = signsIterator.next()
          val parentClass = PsiTreeUtil.getParentOfType(node.info.namedElement, classOf[PsiClass])
          if (parentClass == inheritor) {
            val supersIterator = node.supers.iterator
            while (supersIterator.hasNext) {
              val s = supersIterator.next()
              if (s.info.namedElement eq originalMember) {
                resultBuffer += node.info.namedElement
                return deep
              }
            }
          }
        }
    }
    true
  }
}

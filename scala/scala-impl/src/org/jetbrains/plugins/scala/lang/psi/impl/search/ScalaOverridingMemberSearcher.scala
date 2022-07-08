package org.jetbrains.plugins.scala
package lang
package psi
package impl
package search

import com.intellij.psi._
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.OverridingMethodsSearch.SearchParameters
import com.intellij.psi.search.searches.{ClassInheritorsSearch, OverridingMethodsSearch}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias}
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
             if !namedElement.isInstanceOf[PsiMethod] || !implementation.isInstanceOf[PsiMethod]) {
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
             if implementation.isInstanceOf[PsiMethod]) {
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

  def search(member: PsiNamedElement,
             scopeOption: Option[SearchScope] = None,
             deep: Boolean = true,
             withSelfType: Boolean = false): Array[PsiNamedElement] = {
    val scope = scopeOption.getOrElse(member.getUseScope)

    def inTemplateBodyOrEarlyDef(element: PsiElement) = element.getParent match {
      case _: ScTemplateBody | _: ScEarlyDefinitions => true
      case _                                         => false
    }

    member match {
      case _: ScFunction | _: ScTypeAlias           => if (!inTemplateBodyOrEarlyDef(member)) return Array.empty
      case td: ScTypeDefinition if !td.isObject     => if (!inTemplateBodyOrEarlyDef(member)) return Array.empty
      case cp: ScClassParameter if cp.isClassMember =>
      case x: PsiNamedElement =>
        val nameContext = ScalaPsiUtil.nameContext(x)
        if (nameContext == null || !inTemplateBodyOrEarlyDef(nameContext)) return Array.empty
      case _ => return Array.empty
    }

    val parentClass = member match {
      case m: PsiMethod       => m.containingClass
      case x: PsiNamedElement => PsiTreeUtil.getParentOfType(x, classOf[ScTemplateDefinition])
    }

    // e.g. if `member` is function inside Scala3 `given`
    if (parentClass == null) return Array.empty

    if (parentClass.isEffectivelyFinal) return Array.empty

    val buffer = mutable.Set.empty[PsiNamedElement]

    def process(inheritor: PsiClass): Boolean = {
      def inheritorsOfType(name: String): Boolean = {
        inheritor match {
            case inheritor: ScTypeDefinition =>
              for (aliass <- inheritor.aliases if name == aliass.name) {
                buffer += aliass
                if (!deep) return false
              }
              for (td <- inheritor.typeDefinitions if !td.isObject && name == td.name) {
                buffer += td
                if (!deep) return false
              }
            case _ =>
          }
        true
      }

      inReadAction {
        member match {
          case alias: ScTypeAlias =>
            val continue = inheritorsOfType(alias.name)
            if (!continue) return false
          case td: ScTypeDefinition if !td.isObject =>
            val continue = inheritorsOfType(td.name)
            if (!continue) return false
          case _: PsiNamedElement =>
            val signatures =
              if (withSelfType) TypeDefinitionMembers.getSelfTypeSignatures(inheritor)
              else TypeDefinitionMembers.getSignatures(inheritor)
            val signsIterator = signatures.forName(member.name).nodesIterator
            while (signsIterator.hasNext) {
              val node = signsIterator.next()
              if (PsiTreeUtil.getParentOfType(node.info.namedElement, classOf[PsiClass]) == inheritor) {
                val supersIterator = node.supers.iterator
                while (supersIterator.hasNext) {
                  val s = supersIterator.next()
                  if (s.info.namedElement eq member) {
                    buffer += node.info.namedElement
                    return deep
                  }
                }
              }
            }
        }
      }
      true
    }

    var break = false
    val inheritors = inReadAction {
      ClassInheritorsSearch.search(parentClass, scope, true).toArray(PsiClass.EMPTY_ARRAY)
    }
    for (clazz <- inheritors if !break) {
      break = !process(clazz)
    }

    if (withSelfType) {
      val inheritors = ScalaInheritors.getSelfTypeInheritors(parentClass)
      break = false
      for (clazz <- inheritors if !break) {
        break = !process(clazz)
      }
    }

    buffer.toArray
  }
}
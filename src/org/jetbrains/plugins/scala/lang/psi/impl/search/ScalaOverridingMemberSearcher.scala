package org.jetbrains.plugins.scala
package lang
package psi
package impl
package search

import scala.collection.mutable.ArrayBuffer
import api.statements.{ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import api.toplevel.typedef.{ScTypeDefinition, ScTemplateDefinition}
import com.intellij.psi._
import com.intellij.psi.search.searches.{OverridingMethodsSearch, ClassInheritorsSearch}
import search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import toplevel.typedef.TypeDefinitionMembers
import types._
import org.jetbrains.plugins.scala.extensions.{inReadAction, toPsiMemberExt, toPsiNamedElementExt}
import psi.stubs.util.ScalaStubsUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScEarlyDefinitions}
import com.intellij.util.{Processor, QueryExecutor}
import com.intellij.psi.search.searches.OverridingMethodsSearch.SearchParameters

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.11.2008
 */

/**
 * This class is required for Ctrl+Alt+B action for cases when not PsiMethod overrides not PsiMethod (one of two cases)
 */
class MethodImplementationsSearch extends QueryExecutor[PsiElement, PsiElement] {
  override def execute(sourceElement: PsiElement, consumer: Processor[PsiElement]): Boolean = {
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
  def execute(queryParameters: SearchParameters, consumer: Processor[PsiMethod]): Boolean = {
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
  def getOverridingMethods(method: ScNamedElement): Array[PsiNamedElement] = {
    val result = new ArrayBuffer[PsiNamedElement]
    inReadAction {
      for (psiMethod <- ScalaOverridingMemberSearcher.search(method, deep = true)) {
        result += psiMethod
      }
    }
    result.toArray
  }

  def search(member: PsiNamedElement, scopeOption: Option[SearchScope] = None, deep: Boolean = true,
             withSelfType: Boolean = false): Array[PsiNamedElement] = {
    val scope = scopeOption.getOrElse(member.getUseScope)
    def inTemplateBodyOrEarlyDef(element: PsiElement) = element.getParent match {
        case _: ScTemplateBody | _: ScEarlyDefinitions => true
        case _ => false
    }
    member match {
      case _: ScFunction | _: ScTypeAlias => if (!inTemplateBodyOrEarlyDef(member)) return Array[PsiNamedElement]()
      case td: ScTypeDefinition if !td.isObject => if (!inTemplateBodyOrEarlyDef(member)) return Array[PsiNamedElement]()
      case cp: ScClassParameter if cp.isEffectiveVal =>
      case x: PsiNamedElement =>
        val nameContext = ScalaPsiUtil.nameContext(x)
        if (nameContext == null || !inTemplateBodyOrEarlyDef(nameContext)) return Array[PsiNamedElement]()
      case _: PsiMethod =>
      case _ => return Array[PsiNamedElement]()
    }

    val parentClass = member match {
      case m: PsiMethod => m.containingClass
      case x: PsiNamedElement => PsiTreeUtil.getParentOfType(x, classOf[ScTemplateDefinition])
    }
    val buffer = new ArrayBuffer[PsiNamedElement]

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
          val signsIterator = signatures.forName(member.name)._1.iterator
          while (signsIterator.hasNext) {
            val (t: Signature, node: TypeDefinitionMembers.SignatureNodes.Node) = signsIterator.next()
            if (t.namedElement != None && PsiTreeUtil.getParentOfType(t.namedElement.get,
              classOf[PsiClass]) == inheritor) {
              val supersIterator = node.supers.iterator
              while (supersIterator.hasNext) {
                val s = supersIterator.next()
                if (s.info.namedElement.get eq member) {
                  buffer += t.namedElement.get
                  return deep
                }
              }
            }
          }
      }
      true
    }

    var break = false
    val inheritors = ClassInheritorsSearch.search(parentClass, scope, true).toArray(PsiClass.EMPTY_ARRAY)
    for (clazz <- inheritors if !break) {
      break = !process(clazz)
    }

    if (withSelfType) {
      val inheritors = ScalaStubsUtil.getSelfTypeInheritors(parentClass, parentClass.getResolveScope)
      break = false
      for (clazz <- inheritors if !break) {
        break = !process(clazz)
      }
    }

    buffer.toArray
  }
}
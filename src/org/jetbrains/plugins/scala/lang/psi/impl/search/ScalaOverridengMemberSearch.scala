package org.jetbrains.plugins.scala
package lang
package psi
package impl
package search

import _root_.scala.collection.mutable.ArrayBuffer
import api.statements.{ScFunction, ScTypeAlias}
import api.toplevel.templates.ScTemplateBody
import api.toplevel.typedef.{ScTypeDefinition, ScTemplateDefinition}
import com.intellij.psi._
import com.intellij.psi.search.searches.{OverridingMethodsSearch, ClassInheritorsSearch, ExtensibleQueryFactory}
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.{QueryFactory, EmptyQuery, Query}
import java.util.Arrays
import toplevel.typedef.TypeDefinitionMembers
import api.toplevel.ScTypedDefinition
import types._

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.11.2008
 */

object ScalaOverridengMemberSearch {
  def search(member: PsiNamedElement, scope: SearchScope, deep: Boolean): Array[PsiNamedElement] = {
    member match {
      case _: ScFunction =>  if (!member.getParent.isInstanceOf[ScTemplateBody]) return Array[PsiNamedElement]()
      case _: ScTypeAlias => if (!member.getParent.isInstanceOf[ScTemplateBody]) return Array[PsiNamedElement]()
      case td: ScTypeDefinition if !td.isObject =>
        if (!member.getParent.isInstanceOf[ScTemplateBody]) return Array[PsiNamedElement]()
      case x: PsiNamedElement if ScalaPsiUtil.nameContext(x) != null &&
        ScalaPsiUtil.nameContext(x).getParent.isInstanceOf[ScTemplateBody] =>
      case _: PsiMethod =>
      case _ => return Array[PsiNamedElement]()
    }

    val parentClass = member match {
      case m: PsiMethod => m.getContainingClass
      case x: PsiNamedElement => PsiTreeUtil.getParentOfType(x, classOf[ScTemplateDefinition])
    }
    val buffer = new ArrayBuffer[PsiNamedElement]

    def process(inheritor: PsiClass): Boolean = {
      def inheritorsOfType(name: String): Boolean = {
        inheritor match {
            case inheritor: ScTypeDefinition =>
              for (aliass <- inheritor.aliases if name == aliass.getName) {
                buffer += aliass
                if (!deep) return false
              }
              for (td <- inheritor.typeDefinitions if !td.isObject && name == td.getName) {
                buffer += td
                if (!deep) return false
              }
            case _ =>
          }
        true
      }

      member match {
        case alias: ScTypeAlias =>
          val continue = inheritorsOfType(alias.getName)
          if (!continue) return false
        case td: ScTypeDefinition if !td.isObject =>
          val continue = inheritorsOfType(td.getName)
          if (!continue) return false
        case _: PsiNamedElement =>
          val signsIterator = TypeDefinitionMembers.getSignatures(inheritor).forName(member.getName)._1.iterator
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

    buffer.toArray
  }

  def search(member: PsiNamedElement, checkDeep: Boolean): Array[PsiNamedElement] =
    search(member, member.getUseScope, checkDeep)

  def search(member: PsiNamedElement): Array[PsiNamedElement] = search(member, true)
}
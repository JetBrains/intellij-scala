package org.jetbrains.plugins.scala.lang.psi.impl.search

import _root_.scala.collection.mutable.ArrayBuffer
import api.statements.ScFunction
import api.toplevel.templates.ScTemplateBody
import com.intellij.psi.search.searches.{OverridingMethodsSearch, ClassInheritorsSearch, ExtensibleQueryFactory}
import com.intellij.psi.{PsiMember, PsiMethod, PsiClass}
import com.intellij.psi.search.SearchScope
import com.intellij.util.{QueryFactory, EmptyQuery, Query}
import toplevel.typedef.TypeDefinitionMembers
import types.{Bounds, FullSignature, ScSubstitutor}

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.11.2008
 */

object OverridingMemberSearch {
  def search(member: PsiMember, scope: SearchScope, deep: Boolean): Array[PsiMember] = {
    if (!member.getParent.isInstanceOf[ScTemplateBody]) return Array[PsiMember]()

    val parentClass = member.getContainingClass
    val buffer = new ArrayBuffer[PsiMember]

    def process(inheritor: PsiClass): Boolean = {
      val substitutor = Bounds.superSubstitutor(parentClass, inheritor, ScSubstitutor.empty) match {
        case Some(x) => x
        case None => return true
      }
      member match {
        case method: ScFunction => {
          /*val signatures: Seq[FullSignature] = TypeDefinitionMembers.getSignatures(inheritor).values.map{n => n.info}.
                  toArray.toSeq.map(_.asInstanceOf[FullSignature])
          true*/
          true
        }
        case _ => true
      }
    }

    var break = false
    for (clazz <- ClassInheritorsSearch.search(parentClass, scope, true).toArray(PsiClass.EMPTY_ARRAY) if !break) {
      break = process(clazz)
    }

    buffer.toArray
  }

  def search(member: PsiMember, checkDeep: Boolean): Array[PsiMember] = search(member, member.getUseScope(), checkDeep)

  def search(member: PsiMember): Array[PsiMember] = search(member, true)
}
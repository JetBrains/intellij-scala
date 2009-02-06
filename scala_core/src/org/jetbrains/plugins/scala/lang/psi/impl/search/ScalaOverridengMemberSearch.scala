package org.jetbrains.plugins.scala.lang.psi.impl.search

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
import types.{Bounds, FullSignature, PhysicalSignature, ScSubstitutor}

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.11.2008
 */

object ScalaOverridengMemberSearch {
  def search(member: PsiNamedElement, scope: SearchScope, deep: Boolean): Array[PsiNamedElement] = {
    member match {
      case _: ScFunction =>  if (!member.getParent.isInstanceOf[ScTemplateBody]) return Array[PsiNamedElement]()
      case _: ScTypeAlias => if (!member.getParent.isInstanceOf[ScTemplateBody]) return Array[PsiNamedElement]()
      case x: PsiNamedElement if ScalaPsiUtil.nameContext(x) != null && ScalaPsiUtil.nameContext(x).getParent.isInstanceOf[ScTemplateBody] =>
      case _: PsiMethod =>
      case _ => return Array[PsiNamedElement]()
    }


    val parentClass = member match {
      case m: PsiMethod => m.getContainingClass
      case x: PsiNamedElement => PsiTreeUtil.getParentOfType(x, classOf[ScTemplateDefinition])
    }
    val buffer = new ArrayBuffer[PsiNamedElement]

    def process(inheritor: PsiClass): Boolean = {
      val substitutor = Bounds.superSubstitutor(parentClass, inheritor, ScSubstitutor.empty) match {
        case Some(x) => x
        case None => return true
      }
      val signatures: Seq[FullSignature] = TypeDefinitionMembers.getSignatures(inheritor).values.map{n => n.info}.
                  collect.map(_.asInstanceOf[FullSignature]).
                  filter((x: FullSignature) => PsiTreeUtil.getParentOfType(x.element, classOf[PsiClass]) == inheritor)
      member match {
        case method: PsiMethod => {
          val sign = new PhysicalSignature(method, substitutor)
          for (signature <- signatures if sign.equiv(signature.sig)) {
            buffer += signature.element.asInstanceOf[PsiNamedElement]
            if (!deep) return false
          }
        }
        case alias: ScTypeAlias => {
          inheritor match {
            case inheritor: ScTypeDefinition => for (aliass <- inheritor.aliases if alias.getName == aliass.getName) {
              buffer += aliass
              if (!deep) return false
            }
            case _ =>
          }
        }
        case x: PsiNamedElement => {
          val sign = ScalaPsiUtil.namedElementSig(x)
          for (signature <- signatures if sign.equiv(signature.sig)) {
            buffer += signature.element.asInstanceOf[PsiNamedElement]
            if (!deep) return false
          }
        }
      }
      true
    }

    var break = false
    for (clazz <- ClassInheritorsSearch.search(parentClass, scope, true).toArray(PsiClass.EMPTY_ARRAY) if !break) {
      break = process(clazz)
    }

    buffer.toArray
  }

  def search(member: PsiNamedElement, checkDeep: Boolean): Array[PsiNamedElement] = search(member, member.getUseScope(), checkDeep)

  def search(member: PsiNamedElement): Array[PsiNamedElement] = search(member, true)
}
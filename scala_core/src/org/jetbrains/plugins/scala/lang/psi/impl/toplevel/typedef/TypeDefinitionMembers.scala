package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements._

import org.jetbrains.plugins.scala.lang.psi.types.Signature
object MethodNodes extends MixinNodes {
  type T = Signature
  def equiv(s1 : Signature, s2 : Signature) = s1 equiv s2
  def computeHashCode(s : Signature) = s.name.hashCode* 31 + s.types.length
}

import com.intellij.psi.PsiNamedElement
object ValueNodes extends MixinNodes {
  type T = PsiNamedElement
  def equiv(p1 : PsiNamedElement, p2 : PsiNamedElement) = p1.getName == p2.getName
  def computeHashCode(patt : PsiNamedElement) = patt.getName.hashCode
}

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
object TypeAliasNodes extends MixinNodes {
  type T = ScTypeAlias
  def equiv(al1 : ScTypeAlias, al2 : ScTypeAlias) = al1.name == al2.name
  def computeHashCode(al : ScTypeAlias) = al.name.hashCode
}

object TypeDefinitionMembers {
  def process(td : ScTypeDefinition) {
    def inner(clazz : PsiClass, subst : ScSubstitutor) = {
      val valuesMap = new ValueNodes.Map
      val aliasesMap = new TypeAliasNodes.Map
      val methodsMap = new MethodNodes.Map
      clazz match {
        case td : ScTypeDefinition => {
          for (member <- td.members) {
            member match {
              case method : ScFunction => {
                val sig = new Signature(method, subst)
                methodsMap += ((sig, new MethodNodes.Node(sig)))
              }
              case alias : ScTypeAlias => aliasesMap += ((alias, new TypeAliasNodes.Node(alias)))
              case obj : ScObject => valuesMap += ((obj, new ValueNodes.Node(obj)))
              case patternDef : ScPatternDefinition => for (binding <- patternDef.bindings) {
                valuesMap += ((binding, new ValueNodes.Node(binding)))
              }
              case varDef : ScVariableDefinition => for (binding <- varDef.bindings) {
                valuesMap += ((binding, new ValueNodes.Node(binding)))
              }
              case _ =>
            }
          }
        }
        case _ => {
          for (method <- clazz.getMethods) {
            val sig = new Signature(method, subst)
            methodsMap += ((sig, new MethodNodes.Node(sig)))
          }

          for (field <- clazz.getFields) {
            valuesMap += ((field, new ValueNodes.Node(field)))
          }
        }
      }
      (valuesMap, methodsMap, aliasesMap)
    }
    inner(td, ScSubstitutor.empty)
  }
}
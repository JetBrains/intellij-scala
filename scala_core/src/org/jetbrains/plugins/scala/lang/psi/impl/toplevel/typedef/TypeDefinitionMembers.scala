package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

object TypeDefinitionMembers {
  def process(td : ScTypeDefinition) {
    def inner(clazz : PsiClass, subst : ScSubstitutor) = {
      val valuesMap = new ValueNodes.Map
      val aliasesMap = new TypeAliasNodes.Map
      val methodsMap = new MethodNodes.Map
      clazz match {
        case td : ScTypeDefinitionImpl => {
          for (member <- td.members) {
            member match {
              case method : ScFunction => {
                val sig = new Signature(method, subst)
                methodsMap += ((sig, new MethodNodes.Node(sig)))
              }
              //case alias : ScTypeAlias => aliasesMap += ((alias, new TypeAliasNodes.Node(alias)))
              case obj : ScObject => valuesMap += ((obj, new ValueNodes.Node(obj)))
              case _ => //todo
            }
          }
        }
        case _ =>() //todo java case
      }
      (valuesMap, methodsMap, aliasesMap)
    }
    inner(td, ScSubstitutor.empty)
  }
}
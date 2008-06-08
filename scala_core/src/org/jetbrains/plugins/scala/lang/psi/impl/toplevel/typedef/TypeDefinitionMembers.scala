package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.types.Signature
import _root_.scala.collection.mutable.ListBuffer
import com.intellij.openapi.util.Key
import util._

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
object TypeNodes extends MixinNodes {
  type T = PsiNamedElement //class or type alias
  def equiv(t1 : PsiNamedElement, t2 : PsiNamedElement) = t1.getName == t2.getName
  def computeHashCode(t : PsiNamedElement) = t.getName.hashCode
}

object TypeDefinitionMembers {
  import ValueNodes.{Map => Vmap}, MethodNodes.{Map => Mmap}, TypeNodes.{Map => Tmap}
  import ValueNodes.{Node => Vnode}, MethodNodes.{Node => Mnode}, TypeNodes.{Node => Tnode}

  private def build(td : ScTypeDefinition) = {
    def inner(clazz : PsiClass, subst : ScSubstitutor) : Triple[Vmap, Mmap, Tmap] = {
      val valuesMap = new Vmap
      val methodsMap = new Mmap
      val typesMap = new Tmap
      val superTypes = clazz match {
        case td : ScTypeDefinition => {
          for (member <- td.members) {
            member match {
              case method : ScFunction => {
                val sig = new Signature(method, subst)
                methodsMap += ((sig, new Mnode(sig)))
              }
              case alias : ScTypeAlias => typesMap += ((alias, new Tnode(alias)))
              case obj : ScObject => valuesMap += ((obj, new Vnode(obj)))
              case td : ScTypeDefinition => typesMap += ((td, new Tnode(td)))
              case patternDef : ScPatternDefinition =>
                for (binding <- patternDef.bindings) {
                  valuesMap += ((binding, new Vnode(binding)))
                }
              case varDef : ScVariableDefinition =>
                for (binding <- varDef.bindings) {
                  valuesMap += ((binding, new Vnode(binding)))
                }
              case _ =>
            }
          }

          td.superTypes
        }
        case _ => {
          for (method <- clazz.getMethods) {
            val sig = new Signature(method, subst)
            methodsMap += ((sig, new Mnode(sig)))
          }

          for (field <- clazz.getFields) {
            valuesMap += ((field, new Vnode(field)))
          }

          for (inner <- clazz.getInnerClasses) {
            typesMap += ((inner, new Tnode(inner)))
          }

          clazz.getSuperTypes.map {psiType => ScType.create(psiType, clazz.getProject)}
        }
      }

      val superValsBuff = new ListBuffer[Vmap]
      val superMethodsBuff = new ListBuffer[Mmap]
      val superAliasesBuff = new ListBuffer[Tmap]
      for (superType <- superTypes) {
        superType match {
          case ScParameterizedType(superClass : PsiClass, superSubst) => {
            val (superVals, superMethods, superAliases) = inner (superClass, combine(superSubst, subst))
            superValsBuff += superVals
            superMethodsBuff += superMethods
            superAliasesBuff += superAliases
          }
          case _ =>
        }
      }
      ValueNodes.mergeWithSupers(valuesMap, ValueNodes.mergeSupers(superValsBuff.toList))
      MethodNodes.mergeWithSupers(methodsMap, MethodNodes.mergeSupers(superMethodsBuff.toList))
      TypeNodes.mergeWithSupers(typesMap, TypeNodes.mergeSupers(superAliasesBuff.toList))

      (valuesMap, methodsMap, typesMap)
    }
    inner(td, ScSubstitutor.empty)
  }

  def combine(superSubst : ScSubstitutor, derived : ScSubstitutor) = {
    var res : ScSubstitutor = ScSubstitutor.empty
    for ((tp, t) <- superSubst.map) {
      res = res + (tp, derived.subst(t))
    }
    res
  }

  val key : Key[CachedValue[Triple[Vmap, Mmap, Tmap]]] = Key.create("members key")

  def getMembers(td : ScTypeDefinition) = {
    var computed = td.getUserData(key)
    if (computed == null) {
      val manager = PsiManager.getInstance(td.getProject).getCachedValuesManager
      computed = manager.createCachedValue(new MyProvider(td), false)
    }
    computed.getValue
  }

  class MyProvider(td : ScTypeDefinition)
    extends CachedValueProvider[Triple[Vmap, Mmap, Tmap]] {
    def compute() = new CachedValueProvider.Result (build(td),
                         Array[Object](PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT))
  }
}
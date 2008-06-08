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
  private def build(td : ScTypeDefinition) = {
    def inner(clazz : PsiClass, subst : ScSubstitutor) : Tuple3[ValueNodes.Map, MethodNodes.Map, TypeNodes.Map] = {
      val valuesMap = new ValueNodes.Map
      val typesMap = new TypeNodes.Map
      val methodsMap = new MethodNodes.Map
      val superTypes = clazz match {
        case td : ScTypeDefinition => {
          for (member <- td.members) {
            member match {
              case method : ScFunction => {
                val sig = new Signature(method, subst)
                methodsMap += ((sig, new MethodNodes.Node(sig)))
              }
              case alias : ScTypeAlias => typesMap += ((alias, new TypeNodes.Node(alias)))
              case obj : ScObject => valuesMap += ((obj, new ValueNodes.Node(obj)))
              case td : ScTypeDefinition => typesMap += ((td, new TypeNodes.Node(td)))
              case patternDef : ScPatternDefinition => for (binding <- patternDef.bindings) {
                valuesMap += ((binding, new ValueNodes.Node(binding)))
              }
              case varDef : ScVariableDefinition => for (binding <- varDef.bindings) {
                valuesMap += ((binding, new ValueNodes.Node(binding)))
              }
              case _ =>
            }
          }

          td.superTypes
        }
        case _ => {
          for (method <- clazz.getMethods) {
            val sig = new Signature(method, subst)
            methodsMap += ((sig, new MethodNodes.Node(sig)))
          }

          for (field <- clazz.getFields) {
            valuesMap += ((field, new ValueNodes.Node(field)))
          }

          for (inner <- clazz.getInnerClasses) {
            typesMap += ((inner, new TypeNodes.Node(inner)))
          }

          clazz.getSuperTypes.map {psiType => ScType.create(psiType, clazz.getProject)}
        }
      }

      val superValsBuff = new ListBuffer[ValueNodes.Map]
      val superMethodsBuff = new ListBuffer[MethodNodes.Map]
      val superAliasesBuff = new ListBuffer[TypeNodes.Map]
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

  val key : Key[CachedValue[Tuple3[ValueNodes.Map, MethodNodes.Map, TypeNodes.Map]]] =
    Key.create("members key")

  def getMembers(td : ScTypeDefinition) = {
    var computed = td.getUserData(key)
    if (computed != null) {
      val manager = PsiManager.getInstance(td.getProject).getCachedValuesManager
      computed = manager.createCachedValue(new MyProvider(td), false)
    }
    computed.getValue
  }

  class MyProvider(td : ScTypeDefinition)
    extends CachedValueProvider[Tuple3[ValueNodes.Map, MethodNodes.Map, TypeNodes.Map]] {
    def compute() = {
      new CachedValueProvider.Result[Tuple3[ValueNodes.Map, MethodNodes.Map, TypeNodes.Map]] (build(td),
        Array[Object](PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT))
    }
  }
}
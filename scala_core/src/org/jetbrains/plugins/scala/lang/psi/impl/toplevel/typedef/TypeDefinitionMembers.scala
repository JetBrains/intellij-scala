package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.types.Signature
import _root_.scala.collection.mutable.ListBuffer
import com.intellij.openapi.util.Key
import util._


object TypeDefinitionMembers {
  object MethodNodes extends MixinNodes {
    type T = Signature
    def equiv(s1 : Signature, s2 : Signature) = s1 equiv s2
    def computeHashCode(s : Signature) = s.name.hashCode* 31 + s.types.length
    def isAbstract(s : Signature) = s.method match {
      case _ : ScFunctionDeclaration => true
      case m if m.hasModifierProperty(PsiModifier.ABSTRACT) => true
      case _ => false
    }
  }

  import com.intellij.psi.PsiNamedElement
  object ValueNodes extends MixinNodes {
    type T = PsiNamedElement
    def equiv(n1 : PsiNamedElement, n2 : PsiNamedElement) = n1.getName == n2.getName
    def computeHashCode(named : PsiNamedElement) = named.getName.hashCode
    def isAbstract(named : PsiNamedElement) = named match {
      case _ : ScFieldId => true
      case f : PsiField if f.hasModifierProperty(PsiModifier.ABSTRACT) => true
      case _ => false
    }
  }

  import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
  object TypeNodes extends MixinNodes {
    type T = PsiNamedElement //class or type alias
    def equiv(t1 : PsiNamedElement, t2 : PsiNamedElement) = t1.getName == t2.getName
    def computeHashCode(t : PsiNamedElement) = t.getName.hashCode
    def isAbstract(t : PsiNamedElement) = t match {
      case _ : ScTypeAliasDeclaration => true
      case _ => false
    }
  }

  import ValueNodes.{Map => Vmap}, MethodNodes.{Map => Mmap}, TypeNodes.{Map => Tmap}
  import ValueNodes.{Node => Vnode}, MethodNodes.{Node => Mnode}, TypeNodes.{Node => Tnode}

  private def buildTypes(td : ScTypeDefinition) = {
    def inner(clazz : PsiClass, subst : ScSubstitutor) : Tmap = {
      val typesMap = new Tmap
      val superTypes = clazz match {
        case td : ScTypeDefinition => {
          for (member <- td.members) {
            member match {
              case alias : ScTypeAlias => typesMap += ((alias, new Tnode(alias)))
              case td : ScTypeDefinition => typesMap += ((td, new Tnode(td)))
              case _ =>
            }
          }

          td.superTypes
        }
        case _ => {
          for (inner <- clazz.getInnerClasses) {
            typesMap += ((inner, new Tnode(inner)))
          }

          clazz.getSuperTypes.map {psiType => ScType.create(psiType, clazz.getProject)}
        }
      }

      val superTypesBuff = new ListBuffer[Tmap]
      for (superType <- superTypes) {
        superType match {
          case ScParameterizedType(superClass : PsiClass, superSubst) => {
            superTypesBuff += inner (superClass, combine(superSubst, subst))
          }
          case _ =>
        }
      }
      TypeNodes.mergeWithSupers(typesMap, TypeNodes.mergeSupers(superTypesBuff.toList))

      typesMap
    }
    inner(td, ScSubstitutor.empty)
  }

  private def buildVals(td : ScTypeDefinition) = {
    def inner(clazz : PsiClass, subst : ScSubstitutor) : Pair[Vmap, Mmap] = {
      val valuesMap = new Vmap
      val methodsMap = new Mmap
      val superTypes = clazz match {
        case td : ScTypeDefinition => {
          for (member <- td.members) {
            member match {
              case method : ScFunction => {
                val sig = new Signature(method, subst)
                methodsMap += ((sig, new Mnode(sig)))
              }
              case obj : ScObject => valuesMap += ((obj, new Vnode(obj)))
              case patternDef : ScPatternDefinition =>
                for (binding <- patternDef.bindings) {
                  valuesMap += ((binding, new Vnode(binding)))
                }
              case varDef : ScVariableDefinition =>
                for (binding <- varDef.bindings) {
                  valuesMap += ((binding, new Vnode(binding)))
                }
              case varDecl : ScVariableDeclaration =>
                for (fieldId <- varDecl.getIdList.fieldIds) {
                  valuesMap += ((fieldId, new Vnode(fieldId)))
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

          clazz.getSuperTypes.map {psiType => ScType.create(psiType, clazz.getProject)}
        }
      }

      val superValsBuff = new ListBuffer[Vmap]
      val superMethodsBuff = new ListBuffer[Mmap]
      for (superType <- superTypes) {
        superType match {
          case ScParameterizedType(superClass : PsiClass, superSubst) => {
            val (superVals, superMethods) = inner (superClass, combine(superSubst, subst))
            superValsBuff += superVals
            superMethodsBuff += superMethods
          }
          case _ =>
        }
      }
      ValueNodes.mergeWithSupers(valuesMap, ValueNodes.mergeSupers(superValsBuff.toList))
      MethodNodes.mergeWithSupers(methodsMap, MethodNodes.mergeSupers(superMethodsBuff.toList))

      (valuesMap, methodsMap)
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

  val valsKey : Key[CachedValue[Pair[Vmap, Mmap]]] = Key.create("members key")
  val typesKey : Key[CachedValue[Tmap]] = Key.create("types key")

  def getVals (td : ScTypeDefinition) = get(td, valsKey, new MyProvider(td, {td => buildVals(td)}))
  def getTypes(td : ScTypeDefinition) = get(td, typesKey, new MyProvider(td, {td => buildTypes(td)}))

  private def get[T] (td : ScTypeDefinition, key : Key[CachedValue[T]], provider : CachedValueProvider[T]) = {
    var computed = td.getUserData(key)
    if (computed == null) {
      val manager = PsiManager.getInstance(td.getProject).getCachedValuesManager
      computed = manager.createCachedValue(provider, false)
      td.putUserData(key, computed)
    }
    computed.getValue
  }

  class MyProvider[T](td : ScTypeDefinition, builder : ScTypeDefinition => T) extends CachedValueProvider[T] {
    def compute() = new CachedValueProvider.Result (builder(td),
                         Array[Object](PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT))
  }
}
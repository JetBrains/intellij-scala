/**
* @author ven
*/
package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi._
import types._
import api.toplevel.typedef._
import api.base.ScFieldId
import api.statements._
import types.PhysicalSignature
import _root_.scala.collection.mutable.ListBuffer
import com.intellij.openapi.util.Key
import util._


object TypeDefinitionMembers {
  object MethodNodes extends MixinNodes {
    type T = PhysicalSignature
    def equiv(s1 : PhysicalSignature, s2 : PhysicalSignature) = s1 equiv s2
    def computeHashCode(s : PhysicalSignature) = s.name.hashCode* 31 + s.types.length
    def isAbstract(s : PhysicalSignature) = s.method match {
      case _ : ScFunctionDeclaration => true
      case m if m.hasModifierProperty(PsiModifier.ABSTRACT) => true
      case _ => false
    }

    def processJava(clazz : PsiClass, subst : ScSubstitutor, map : Map) =
      for (method <- clazz.getMethods) {
        val sig = new PhysicalSignature(method, subst)
        map += ((sig, new Node(sig, subst)))
      }

    def processScala(td : ScTypeDefinition, subst : ScSubstitutor, map : Map) =
      for (member <- td.members) {
        member match {
          case method: ScFunction => {
            val sig = new PhysicalSignature(method, subst)
            map += ((sig, new Node(sig, subst)))
          }
          case _ =>
        }
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

    def processJava(clazz : PsiClass, subst : ScSubstitutor, map : Map) =
      for (field <- clazz.getFields) {
        map += ((field, new Node(field, subst)))
      }

    def processScala(td : ScTypeDefinition, subst : ScSubstitutor, map : Map) =
      for (member <- td.members) {
        member match {
          case obj: ScObject => map += ((obj, new Node(obj, subst)))
          case patternDef: ScPatternDefinition =>
            for (binding <- patternDef.bindings) {
              map += ((binding, new Node(binding, subst)))
            }
          case varDef: ScVariableDefinition =>
            for (binding <- varDef.bindings) {
              map += ((binding, new Node(binding, subst)))
            }
          case varDecl: ScVariableDeclaration =>
            for (fieldId <- varDecl.getIdList.fieldIds) {
              map += ((fieldId, new Node(fieldId, subst)))
            }
          case valDecl: ScValueDeclaration =>
            for (fieldId <- valDecl.getIdList.fieldIds) {
              map += ((fieldId, new Node(fieldId, subst)))
            }
          case _ =>
        }
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

    def processJava(clazz: PsiClass, subst : ScSubstitutor, map: Map) =
      for (inner <- clazz.getInnerClasses) {
        map += ((inner, new Node(inner, subst)))
      }

    def processScala(td : ScTypeDefinition, subst : ScSubstitutor, map : Map) = {
      for (member <- td.members) {
        member match {
          case alias: ScTypeAlias => map += ((alias, new Node(alias, subst)))
          case _ =>
        }
      }

      for (inner <- td.typeDefinitions) map += ((inner, new Node(inner, subst)))
    }
  }

  val valsKey : Key[CachedValue[ValueNodes.Map]] = Key.create("vals key")
  val methodsKey : Key[CachedValue[MethodNodes.Map]] = Key.create("methods key")
  val typesKey : Key[CachedValue[TypeNodes.Map]] = Key.create("types key")

  def getVals (td : ScTypeDefinition) = get(td, valsKey, new MyProvider(td, {td => ValueNodes.build(td)}))
  def getMethods (td : ScTypeDefinition) = get(td, methodsKey, new MyProvider(td, {td => MethodNodes.build(td)}))
  def getTypes(td : ScTypeDefinition) = get(td, typesKey, new MyProvider(td, {td => TypeNodes.build(td)}))

  private def get[T] (td : ScTypeDefinition, key : Key[CachedValue[T]], provider : => CachedValueProvider[T]) = {
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
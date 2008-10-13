/**
* @author ven
*/
package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import api.base.{ScFieldId, ScPrimaryConstructor}
import api.toplevel.templates.ScExtendsBlock
import com.intellij.psi.scope.{PsiScopeProcessor, ElementClassHint}
import com.intellij.psi._
import synthetic.{SyntheticClasses, ScSyntheticClass}
import types._
import api.toplevel.typedef._
import api.statements._
import types.PhysicalSignature
import _root_.scala.collection.mutable.ListBuffer
import com.intellij.openapi.util.Key
import util._
import _root_.scala.collection.mutable.HashMap

object TypeDefinitionMembers {
  object MethodNodes extends MixinNodes {
    type T = PhysicalSignature
    def equiv(s1: PhysicalSignature, s2: PhysicalSignature) = s1 equiv s2
    def computeHashCode(s: PhysicalSignature) = s.name.hashCode * 31 + s.types.length
    def isAbstract(s: PhysicalSignature) = s.method match {
      case _: ScFunctionDeclaration => true
      case m if m.hasModifierProperty(PsiModifier.ABSTRACT) => true
      case _ => false
    }

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map) =
      for (method <- clazz.getMethods) {
        val sig = new PhysicalSignature(method, subst)
        map += ((sig, new Node(sig, subst)))
      }

    def processScala(template : ScTemplateDefinition, subst: ScSubstitutor, map: Map) =
      for (member <- template.members) {
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
    def equiv(n1: PsiNamedElement, n2: PsiNamedElement) = n1.getName == n2.getName
    def computeHashCode(named: PsiNamedElement) = named.getName.hashCode
    def isAbstract(named: PsiNamedElement) = named match {
      case _: ScFieldId => true
      case f: PsiField if f.hasModifierProperty(PsiModifier.ABSTRACT) => true
      case _ => false
    }

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map) =
      for (field <- clazz.getFields) {
        map += ((field, new Node(field, subst)))
      }

    def processScala(template : ScTemplateDefinition, subst: ScSubstitutor, map: Map) =
      for (member <- template.members) {
        member match {
          case obj: ScObject => map += ((obj, new Node(obj, subst)))
          case _var: ScVariable =>
            for (dcl <- _var.declaredElements) {
              map += ((dcl, new Node(dcl, subst)))
            }
          case _val: ScValue =>
            for (dcl <- _val.declaredElements) {
              map += ((dcl, new Node(dcl, subst)))
            }
          case constr : ScPrimaryConstructor =>
            for (param <- constr.parameters) {
              map += ((param, new Node(param, subst)))
            }
          case _ =>
        }
      }
  }

  import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

  object TypeNodes extends MixinNodes {
    type T = PsiNamedElement //class or type alias
    def equiv(t1: PsiNamedElement, t2: PsiNamedElement) = t1.getName == t2.getName
    def computeHashCode(t: PsiNamedElement) = t.getName.hashCode
    def isAbstract(t: PsiNamedElement) = t match {
      case _: ScTypeAliasDeclaration => true
      case _ => false
    }

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map) =
      for (inner <- clazz.getInnerClasses) {
        map += ((inner, new Node(inner, subst)))
      }

    def processScala(template : ScTemplateDefinition, subst: ScSubstitutor, map: Map) = {
      for (member <- template.members) {
        member match {
          case alias: ScTypeAlias => map += ((alias, new Node(alias, subst)))
          case _ : ScObject =>
          case td : ScTypeDefinition=> map += ((td, new Node(td, subst)))
          case _ =>
        }
      }
    }
  }

  import ValueNodes.{Map => VMap}, MethodNodes.{Map => MMap}, TypeNodes.{Map => TMap}
  val valsKey: Key[CachedValue[(VMap, VMap)]] = Key.create("vals key")
  val methodsKey: Key[CachedValue[(MMap, MMap)]] = Key.create("methods key")
  val typesKey: Key[CachedValue[(TMap, TMap)]] = Key.create("types key")
  val signaturesKey: Key[CachedValue[HashMap[Signature, ScType]]] = Key.create("signatures key")

  def getVals(clazz: PsiClass) = get(clazz, valsKey, new MyProvider(clazz, { clazz : PsiClass => ValueNodes.build(clazz) }))._2
  def getMethods(clazz: PsiClass) = get(clazz, methodsKey, new MyProvider(clazz, { clazz : PsiClass => MethodNodes.build(clazz) }))._2
  def getTypes(clazz: PsiClass) = get(clazz, typesKey, new MyProvider(clazz, { clazz : PsiClass => TypeNodes.build(clazz) }))._2

  def getSignatures(clazz: PsiClass) = get(clazz, signaturesKey, new SignaturesProvider(clazz))

  def getSuperVals(c: PsiClass) = get(c, valsKey, new MyProvider(c, { c : PsiClass => ValueNodes.build(c) }))._1
  def getSuperMethods(c: PsiClass) = get(c, methodsKey, new MyProvider(c, { c : PsiClass => MethodNodes.build(c) }))._1
  def getSuperTypes(c: PsiClass) = get(c, typesKey, new MyProvider(c, { c : PsiClass => TypeNodes.build(c) }))._1

  private def get[Dom <: PsiElement, T](e: Dom, key: Key[CachedValue[T]], provider: => CachedValueProvider[T]) = {
    var computed = e.getUserData(key)
    if (computed == null) {
      val manager = PsiManager.getInstance(e.getProject).getCachedValuesManager
      computed = manager.createCachedValue(provider, false)
      e.putUserData(key, computed)
    }
    computed.getValue
  }

  class MyProvider[Dom, T](e: Dom, builder: Dom => T) extends CachedValueProvider[T] {
    def compute() = new CachedValueProvider.Result(builder(e),
    Array[Object](PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT))
  }

  class SignaturesProvider(td: PsiClass) extends CachedValueProvider[HashMap[Signature, ScType]] {
    def compute() = {
      val res = new HashMap[Signature, ScType] 
      for ((_, n) <- getVals(td)) {
        val subst = n.substitutor
        n.info match {
          case _var: ScVariable =>
            for (dcl <- _var.declaredElements) {
              val t = dcl.calcType
              res += ((new Signature(dcl.name, Seq.empty, Array(), subst), t))
              res += ((new Signature(dcl.name + "_", Seq.singleton(t), Array(), subst), Unit))
            }
          case _val: ScValue =>
            for (dcl <- _val.declaredElements) {
              res += ((new Signature(dcl.name, Seq.empty, Array(), subst), dcl.calcType))
            }
          case _ =>
        }
      }
      for ((s, _) <- getMethods(td)) {
        import s.substitutor.subst
        val retType = s.method match {
          case func : ScFunction => func.calcType
          case method => method.getReturnType match {
            case null => Unit
            case rt => ScType.create(rt, method.getProject)
          }
        }
        res += ((s, s.substitutor.subst(retType)))
      }
      new CachedValueProvider.Result(res, Array[Object](PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT))
    }
  }

  def processDeclarations(clazz : PsiClass,
                          processor: PsiScopeProcessor,
                          state: ResolveState,
                          lastParent: PsiElement,
                          place: PsiElement) : Boolean =
    processDeclarations(processor, state, lastParent, place, getVals(clazz), getMethods(clazz), getTypes(clazz)) &&
    AnyRef.asClass(clazz.getProject).processDeclarations(processor, state, lastParent, place) &&
    Any.asClass(clazz.getProject).processDeclarations(processor, state, lastParent, place)

  def processSuperDeclarations(clazz : PsiClass,
                          processor: PsiScopeProcessor,
                          state: ResolveState,
                          lastParent: PsiElement,
                          place: PsiElement) : Boolean =
    processDeclarations(processor, state, lastParent, place, getSuperVals(clazz), getSuperMethods(clazz), getSuperTypes(clazz))

  private def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement,
                                  vals: => ValueNodes.Map,
                                  methods: => MethodNodes.Map,
                                  types: => TypeNodes.Map) : Boolean = {
    val substK = state.get(ScSubstitutor.key)
    val subst = if (substK == null) ScSubstitutor.empty else substK
    if (shouldProcessVals(processor)) {
      for ((_, n) <- vals) {
        if (!processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
      }
    }
    if (shouldProcessMethods(processor)) {
      for ((_, n) <- methods) {
        if (!processor.execute(n.info.method, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
      }
    }
    if (shouldProcessTypes(processor)) {
      for ((_, n) <- types) {
        if (!processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
      }
    }

    true
  }

  import scala.lang.resolve._, scala.lang.resolve.ResolveTargets._

  def shouldProcessVals(processor: PsiScopeProcessor) = processor match {
    case BaseProcessor(kinds) => (kinds contains VAR) || (kinds contains VAL) || (kinds contains OBJECT)
    case _ => {
      val hint = processor.getHint(classOf[ElementClassHint])
      hint == null || hint.shouldProcess(classOf[PsiVariable])
    }
  }

  def shouldProcessMethods(processor: PsiScopeProcessor) = processor match {
    case BaseProcessor(kinds) => kinds contains METHOD
    case _ => {
      val hint = processor.getHint(classOf[ElementClassHint])
      hint == null || hint.shouldProcess(classOf[PsiMethod])
    }
  }

  def shouldProcessTypes(processor: PsiScopeProcessor) = processor match {
    case BaseProcessor(kinds) => kinds contains CLASS
    case _ => false //important: do not process inner classes!
  }
}
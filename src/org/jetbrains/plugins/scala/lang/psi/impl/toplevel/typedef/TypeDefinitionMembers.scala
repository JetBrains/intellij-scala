/**
 * @author ven
 */
package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import api.base.{ScFieldId, ScPrimaryConstructor}
import api.statements.params.ScClassParameter
import com.intellij.psi.scope.{PsiScopeProcessor, ElementClassHint}
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import synthetic.{SyntheticClasses, ScSyntheticClass}
import types._
import api.toplevel.typedef._
import api.statements._
import result.TypingContext
import types.PhysicalSignature
import _root_.scala.collection.mutable.ListBuffer
import com.intellij.openapi.util.Key
import util._
import _root_.scala.collection.mutable.HashMap
import lang.resolve.BaseProcessor
import fake.FakePsiMethod
import api.toplevel.ScTypedDefinition
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.text.StringUtil
//import Suspension._

object TypeDefinitionMembers {

  def isAbstract(s: PhysicalSignature) = s.method match {
    case _: ScFunctionDeclaration => true
    case m if m.hasModifierProperty(PsiModifier.ABSTRACT) => true
    case _ => false
  }

  object MethodNodes extends MixinNodes {
    type T = PhysicalSignature

    def equiv(s1: PhysicalSignature, s2: PhysicalSignature) = s1 equiv s2

    def computeHashCode(s: PhysicalSignature) = s.name.hashCode

    def isAbstract(s: PhysicalSignature) = TypeDefinitionMembers.this.isAbstract(s)

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map) =
      for (method <- clazz.getMethods) {
        val sig = new PhysicalSignature(method, subst)
        map += ((sig, new Node(sig, subst)))
      }

    def processSyntheticScala(clazz: ScSyntheticClass, subst: ScSubstitutor, map: Map) {
      clazz.getName match {
        case "Any" => {
          val project = clazz.getProject
          val facade = JavaPsiFacade.getInstance(project)
          val obj = facade.findClass("java.lang.Object", GlobalSearchScope.allScope(project))
          if (obj != null) {
            for (m <- obj.getMethods) {
              val sig = new PhysicalSignature(m, subst)
              map += ((sig, new Node(sig, subst)))
            }
          }
        }
        case _ =>
      }
    }

    def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map) =
      for (member <- template.members) {
        member match {
          case primary: ScPrimaryConstructor => {
            val sig = new PhysicalSignature(primary, subst)
            map += ((sig, new Node(sig, subst)))
          }
          case method: ScFunction => {
            val sig = new PhysicalSignature(method, subst)
            map += ((sig, new Node(sig, subst)))
          }
          case _ =>
        }
      }

    def cachedBuild(clazz: PsiClass): Map = getMethods(clazz)
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

    def processSyntheticScala(clazz: ScSyntheticClass, subst: ScSubstitutor, map: Map) {}

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map) =
      for (field <- clazz.getFields) {
        map += ((field, new Node(field, subst)))
      }

    def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map) =
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
          case constr: ScPrimaryConstructor => {
            val isCase: Boolean = template match {
              case td: ScTypeDefinition if td.isCase => true
              case _ => false
            }
            val parameters = if (isCase) constr.parameters else constr.valueParameters
            for (param <- parameters) {
              map += ((param, new Node(param, subst)))
            }
          }
          case _ =>
        }
      }

    def cachedBuild(clazz: PsiClass): Map = getVals(clazz)
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

    def processSyntheticScala(clazz: ScSyntheticClass, subst: ScSubstitutor, map: Map) {}

    def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map) = {
      for (member <- template.members) {
        member match {
          case alias: ScTypeAlias => map += ((alias, new Node(alias, subst)))
          case _: ScObject =>
          case td: ScTypeDefinition => map += ((td, new Node(td, subst)))
          case _ =>
        }
      }
    }

    def cachedBuild(clazz: PsiClass): Map = getTypes(clazz)
  }

  object SignatureNodes extends MixinNodes {
    type T = FullSignature

    def equiv(s1: FullSignature, s2: FullSignature) = s1.sig equiv s2.sig

    def computeHashCode(s: FullSignature) = s.sig.name.hashCode

    def isAbstract(s: FullSignature) = s.sig match {
      case phys: PhysicalSignature => TypeDefinitionMembers.this.isAbstract(phys)
      case _ => false
    }

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map) =
      for (method <- clazz.getMethods) {
        val phys = new PhysicalSignature(method, subst)
        val psiRet = method.getReturnType
        val retType = if (psiRet == null) Unit else ScType.create(psiRet, method.getProject)
        val sig = new FullSignature(phys, subst.subst(retType), method, clazz)
        map += ((sig, new Node(sig, subst)))
      }

    def processSyntheticScala(clazz: ScSyntheticClass, subst: ScSubstitutor, map: Map) {
      clazz.getName match {
        case "Any" => {
          val project = clazz.getProject
          val facade = JavaPsiFacade.getInstance(project)
          val obj = facade.findClass("java.lang.Object", GlobalSearchScope.allScope(project))
          if (obj != null) {
            for (m <- obj.getMethods) {
              val phys = new PhysicalSignature(m, subst)
              val psiRet = m.getReturnType
              val retType = if (psiRet == null) Unit else ScType.create(psiRet, m.getProject)
              val sig = new FullSignature(phys, subst.subst(retType), m, obj)
              map += ((sig, new Node(sig, subst)))
            }
          }
        }
        case _ =>
      }
    }

    def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map) = {
      def addSignature(s: Signature, ret: ScType, elem: NavigatablePsiElement) {
        val full = new FullSignature(s, ret, elem, template)
        map += ((full, new Node(full, subst)))
      }

      for (member <- template.members) {
        member match {
          case _var: ScVariable =>
            for (dcl <- _var.declaredElements) {
              val t = dcl.getType(TypingContext.empty).getOrElse(Any)
              addSignature(new Signature(dcl.name, Seq.empty, 0, subst), t, dcl)
              addSignature(new Signature(dcl.name + "_", Seq.singleton(t), 1, subst), Unit, dcl)
            }
          case _val: ScValue =>
            for (dcl <- _val.declaredElements) {
              addSignature(new Signature(dcl.name, Seq.empty, 0, subst), dcl.getType(TypingContext.empty).getOrElse(Any), dcl)
            }
          case constr: ScPrimaryConstructor => {
            val isCase: Boolean = template match {
              case td: ScTypeDefinition if td.isCase => true
              case _ => false
            }
            val parameters = if (isCase) constr.parameters else constr.valueParameters
            for (param <- parameters) {
              val t = param.getType(TypingContext.empty).getOrElse(Any)
              addSignature(new Signature(param.name, Seq.empty, 0, subst), t, param)
              if (!param.isStable) addSignature(new Signature(param.name + "_", Seq.singleton(t), 1, subst), Unit, param)
            }
          }
          case f: ScFunction => addSignature(new PhysicalSignature(f, subst), subst.subst(f.returnType.getOrElse(Any)), f)
          case _ =>
        }
      }
    }

    def cachedBuild(clazz: PsiClass): Map = getSignatures(clazz)
  }

  import ValueNodes.{Map => VMap}, MethodNodes.{Map => MMap}, TypeNodes.{Map => TMap}, SignatureNodes.{Map => SMap}
  val valsKey: Key[CachedValue[(VMap, VMap)]] = Key.create("vals key")
  val methodsKey: Key[CachedValue[(MMap, MMap)]] = Key.create("methods key")
  val typesKey: Key[CachedValue[(TMap, TMap)]] = Key.create("types key")
  val signaturesKey: Key[CachedValue[(SMap, SMap)]] = Key.create("signatures key")

  def getVals(clazz: PsiClass): VMap = get(clazz, valsKey, new MyProvider(clazz, {clazz: PsiClass => ValueNodes.build(clazz)}))._2

  def getMethods(clazz: PsiClass): MMap = get(clazz, methodsKey, new MyProvider(clazz, {clazz: PsiClass => MethodNodes.build(clazz)}))._2

  def getTypes(clazz: PsiClass) = get(clazz, typesKey, new MyProvider(clazz, {clazz: PsiClass => TypeNodes.build(clazz)}))._2

  def getSignatures(c: PsiClass): SMap = get(c, signaturesKey, new MyProvider(c, {c: PsiClass => SignatureNodes.build(c)}))._2

  def getSuperVals(c: PsiClass) = get(c, valsKey, new MyProvider(c, {c: PsiClass => ValueNodes.build(c)}))._1

  def getSuperMethods(c: PsiClass) = get(c, methodsKey, new MyProvider(c, {c: PsiClass => MethodNodes.build(c)}))._1

  def getSuperTypes(c: PsiClass) = get(c, typesKey, new MyProvider(c, {c: PsiClass => TypeNodes.build(c)}))._1

  private def get[Dom <: PsiElement, T](e: Dom, key: Key[CachedValue[T]], provider: => CachedValueProvider[T]): T = {
    var computed: CachedValue[T] = e.getUserData(key)
    if (computed == null) {
      val manager = PsiManager.getInstance(e.getProject).getCachedValuesManager
      computed = manager.createCachedValue(provider, false)
      e.putUserData(key, computed)
    }
    computed.getValue
  }

  class MyProvider[Dom, T](e: Dom, builder: Dom => T) extends CachedValueProvider[T] {
    def compute() = new CachedValueProvider.Result(builder(e),
      PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT)
  }

  def processDeclarations(clazz: PsiClass,
                          processor: PsiScopeProcessor,
                          state: ResolveState,
                          lastParent: PsiElement,
                          place: PsiElement): Boolean = {
    def methodMap: MethodNodes.Map = {
      val map: MethodNodes.Map = new MethodNodes.Map
      map ++= getMethods(clazz)
      if (!processor.isInstanceOf[BaseProcessor]) {
        clazz match {
          case td: ScTypeDefinition => {
            ScalaPsiUtil.getCompanionModule(td) match {
              case Some(clazz) => map ++= getMethods(clazz)
            }
          }
        }
      }
      map
    }
    def valuesMap: ValueNodes.Map = {
      val map: ValueNodes.Map = new ValueNodes.Map
      map ++= getVals(clazz)
      if (!processor.isInstanceOf[BaseProcessor]) {
        clazz match {
          case td: ScTypeDefinition => {
            ScalaPsiUtil.getCompanionModule(td) match {
              case Some(clazz) => map ++= getVals(clazz)
            }
          }
        }
      }
      map
    }
    processDeclarations(processor, state, lastParent, place, valuesMap, methodMap, getTypes(clazz),
      clazz.isInstanceOf[ScObject]) &&
            AnyRef.asClass(clazz.getProject).getOrElse(return true).processDeclarations(processor, state, lastParent, place) &&
            Any.asClass(clazz.getProject).getOrElse(return true).processDeclarations(processor, state, lastParent, place)
  }

  def processSuperDeclarations(td: ScTemplateDefinition,
                               processor: PsiScopeProcessor,
                               state: ResolveState,
                               lastParent: PsiElement,
                               place: PsiElement): Boolean = {
    processDeclarations(processor, state, lastParent, place, getSuperVals(td), getSuperMethods(td), getSuperTypes(td),
      td.isInstanceOf[ScObject])
   }

  private def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement,
                                  vals: => ValueNodes.Map,
                                  methods: => MethodNodes.Map,
                                  types: => TypeNodes.Map,
                                  isObject: Boolean): Boolean = {
    val substK = state.get(ScSubstitutor.key)
    val subst = if (substK == null) ScSubstitutor.empty else substK
    if (shouldProcessVals(processor) || (!processor.isInstanceOf[BaseProcessor] && shouldProcessMethods(processor))) {
      val v1 = shouldProcessVals(processor)
      val v2 = !processor.isInstanceOf[BaseProcessor] && shouldProcessMethods(processor)
      for ((_, n) <- vals) {
        ProgressManager.checkCanceled
        if (v1 && !processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
        //this is for Java: to find methods, which is vals in Scala
        if (v2) {
          n.info match {
            case t: ScTypedDefinition => {
              val context = ScalaPsiUtil.nameContext(t)
              if (!processor.execute(new FakePsiMethod(t, context match {
                case o: PsiModifierListOwner => o.hasModifierProperty _
                case _ => (s: String) => false
              }), state)) return false
              context match {
                case annotated: ScAnnotationsHolder => {
                  annotated.hasAnnotation("scala.reflect.BeanProperty") match {
                    case Some(_) => {
                      context match {
                        case value: ScValue => {
                          if (!processor.execute(new FakePsiMethod(t, "get" + StringUtil.capitalize(t.getName),
                            Array.empty, t.getType(TypingContext.empty).getOrElse(Any), value.hasModifierProperty _), state)) return false
                        }
                        case variable: ScVariable => {
                          if (!processor.execute(new FakePsiMethod(t, "get" + StringUtil.capitalize(t.getName),
                            Array.empty, t.getType(TypingContext.empty).getOrElse(Any), variable.hasModifierProperty _), state)) return false
                          if (!processor.execute(new FakePsiMethod(t, "set" + StringUtil.capitalize(t.getName),
                            Array[ScType](t.getType(TypingContext.empty).getOrElse(Any)), Unit, variable.hasModifierProperty _), state)) return false
                        }
                        case _ =>
                      }
                    }
                    case None =>
                  }
                  annotated.hasAnnotation("scala.reflect.BooleanBeanProperty") match {
                    case Some(_) => {
                      context match {
                        case value: ScValue => {
                          if (!processor.execute(new FakePsiMethod(t, "is" + StringUtil.capitalize(t.getName),
                            Array.empty, t.getType(TypingContext.empty).getOrElse(Any), value.hasModifierProperty _), state)) return false
                        }
                        case variable: ScVariable => {
                          if (!processor.execute(new FakePsiMethod(t, "is" + StringUtil.capitalize(t.getName),
                            Array.empty, t.getType(TypingContext.empty).getOrElse(Any), variable.hasModifierProperty _), state)) return false
                          if (!processor.execute(new FakePsiMethod(t, "set" + StringUtil.capitalize(t.getName),
                            Array[ScType](t.getType(TypingContext.empty).getOrElse(Any)), Unit, variable.hasModifierProperty _), state)) return false
                        }
                        case _ =>
                      }
                    }
                    case None =>
                  }
                }
                case _ =>
              }
            }
            case _ =>
          }
        }
      }
    }

    if (shouldProcessMethods(processor)) {
      for ((_, n) <- methods) {
        ProgressManager.checkCanceled
        val substitutor = n.substitutor followed subst
        if (!processor.execute(n.info.method,
          state.put(ScSubstitutor.key, substitutor))) return false
      }
    }
    if (shouldProcessTypes(processor)) {
      for ((_, n) <- types) {
        ProgressManager.checkCanceled
        if (!processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
      }
    }
    //static inner classes
    if (isObject && shouldProcessJavaInnerClasses(processor)) {
      for ((_, n) <- types if n.info.isInstanceOf[ScTypeDefinition]) {
        ProgressManager.checkCanceled
        if (!processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
      }
    }

    true
  }

  import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._

  def shouldProcessVals(processor: PsiScopeProcessor) = processor match {
    case BaseProcessor(kinds) => (kinds contains VAR) || (kinds contains VAL) || (kinds contains OBJECT)
    case _ => {
      val hint: ElementClassHint = processor.getHint(ElementClassHint.KEY)
      hint == null || hint.shouldProcess(ElementClassHint.DeclaractionKind.VARIABLE)
    }
  }

  def shouldProcessMethods(processor: PsiScopeProcessor) = processor match {
    case BaseProcessor(kinds) => kinds contains METHOD
    case _ => {
      val hint = processor.getHint(ElementClassHint.KEY)
      hint == null || hint.shouldProcess(ElementClassHint.DeclaractionKind.METHOD)
    }
  }

  def shouldProcessTypes(processor: PsiScopeProcessor) = processor match {
    case BaseProcessor(kinds) => (kinds contains CLASS) || (kinds contains METHOD)
    case _ => false //important: do not process inner classes!
  }

  def shouldProcessJavaInnerClasses(processor: PsiScopeProcessor): Boolean = {
    if (processor.isInstanceOf[BaseProcessor]) return false
    val hint = processor.getHint(ElementClassHint.KEY)
    hint == null || hint.shouldProcess(ElementClassHint.DeclaractionKind.CLASS)
  }
}

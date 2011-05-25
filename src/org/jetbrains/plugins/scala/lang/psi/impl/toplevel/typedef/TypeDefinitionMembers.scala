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
import com.intellij.psi._
import impl.compiled.ClsClassImpl
import impl.light.LightMethod
import scope.{NameHint, PsiScopeProcessor, ElementClassHint}
import api.toplevel.typedef._
import api.statements._
import types.result.TypingContext
import com.intellij.openapi.util.Key
import fake.FakePsiMethod
import api.toplevel.ScTypedDefinition
import com.intellij.openapi.progress.ProgressManager
import util._
import lang.resolve.processor.BaseProcessor
import synthetic.ScSyntheticClass
import lang.resolve.ResolveUtils
import reflect.NameTransformer
import com.intellij.openapi.diagnostic.Logger
import types._

object TypeDefinitionMembers {
  private val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers")

  def isAccessible(place: Option[PsiElement], member: PsiMember): Boolean = {
    if (place == None) return true
    ResolveUtils.isAccessible(member, place.get)
  }

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

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      for (method <- clazz.getMethods if (isAccessible(place, method) &&
        !method.isConstructor && !method.hasModifierProperty("static"))) {
        val sig = new PhysicalSignature(method, subst)
        map += ((sig, new Node(sig, subst)))
      }
    }

    def processSyntheticScala(clazz: ScSyntheticClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      /*for (method <- clazz.syntheticMethods(place.map(_.getResolveScope).
        getOrElse(GlobalSearchScope.allScope(clazz.getProject)))) {
        val sig = new PhysicalSignature(method, subst)
        map += (sig, new Node(sig, subst))
      }*/
    }

    def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      for (member <- template.members) {
        member match {
          case method: ScFunction if isAccessible(place, method) &&
                  !method.isConstructor => {
            val sig = new PhysicalSignature(method, subst)
            map += ((sig, new Node(sig, subst)))
          }
          case _ =>
        }
      }
      template match {
        case obj: ScObject =>
          for (method <- obj.objectSyntheticMembers) {
            val sig = new PhysicalSignature(method, subst)
            map += ((sig, new Node(sig, subst)))
          }
        case _ =>
      }
    }

    def processRefinement(cp: ScCompoundType, map: Map, place: Option[PsiElement]) {
      for (decl <- cp.decls) {
        decl match {
          case fun: ScFunction if isAccessible(place, fun) => {
            val sig = new PhysicalSignature(fun, cp.subst)
            map += ((sig, new Node(sig, cp.subst)))
          }
          case _ =>
        }
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

    def processSyntheticScala(clazz: ScSyntheticClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {}

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) =
      for (field <- clazz.getFields if (isAccessible(place, field) &&
              !field.hasModifierProperty("static"))) {
        map += ((field, new Node(field, subst)))
      }

    def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) =
      for (member <- template.members) {
        member match {
          case c: ScClass if c.isCase && c.fakeCompanionModule != None && isAccessible(place, c) =>
            val obj = c.fakeCompanionModule.get
            map += ((obj, new Node(obj, subst)))
          case obj: ScObject if isAccessible(place, obj) => map += ((obj, new Node(obj, subst)))
          case _var: ScVariable if isAccessible(place, _var) =>
            for (dcl <- _var.declaredElements) {
              map += ((dcl, new Node(dcl, subst)))
            }
          case _val: ScValue if isAccessible(place, _val) =>
            for (dcl <- _val.declaredElements) {
              map += ((dcl, new Node(dcl, subst)))
            }
          case constr: ScPrimaryConstructor => {
            val isCase: Boolean = template match {
              case td: ScTypeDefinition if td.isCase => true
              case _ => false
            }
            val parameters = constr.parameters
            for (param <- parameters if isAccessible(place, param)) {
              if (!param.isVal && !param.isVar && place != None && template.extendsBlock == place.get && !isCase) {
                //this is class parameter without val or var, it's like private val
                map += ((param, new Node(param, subst)))
              } else if (isAccessible(place, param)) {
                map += ((param, new Node(param, subst)))
              }
            }
          }
          case _ =>
        }
      }

    def processRefinement(cp: ScCompoundType, map: Map, place: Option[PsiElement]) {
      for (decl <- cp.decls) decl match {
        case valDecl: ScValue if isAccessible(place, valDecl) =>
          for (e <- valDecl.declaredElements) {
            map += ((e, new Node(e, cp.subst)))
          }
        case varDecl: ScVariable if isAccessible(place, varDecl) => {
          for (e <- varDecl.declaredElements) {
            map += ((e, new Node(e, cp.subst)))
          }
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

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) =
      for (inner <- clazz.getInnerClasses if isAccessible(place, inner)&&
              !inner.hasModifierProperty("static")) {
        map += ((inner, new Node(inner, subst)))
      }

    def processSyntheticScala(clazz: ScSyntheticClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {}

    def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) = {
      for (member <- template.members) {
        member match {
          case alias: ScTypeAlias if isAccessible(place, alias) => map += ((alias, new Node(alias, subst)))
          case _: ScObject =>
          case td: ScTypeDefinition if isAccessible(place, td) => map += ((td, new Node(td, subst)))
          case _ =>
        }
      }
    }

    def processRefinement(cp: ScCompoundType, map: Map, place: Option[PsiElement]) {
      for (alias <- cp.typeDecls if isAccessible(place, alias)) {
        map += ((alias, new Node(alias, cp.subst)))
      }
    }
  }

  object SignatureNodes extends MixinNodes {
    type T = FullSignature

    def equiv(s1: FullSignature, s2: FullSignature) = s1.sig equiv s2.sig

    def computeHashCode(s: FullSignature) = s.sig.name.hashCode

    def isAbstract(s: FullSignature) = s.sig match {
      case phys: PhysicalSignature => TypeDefinitionMembers.this.isAbstract(phys)
      case _ => false
    }

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) =
      for (method <- clazz.getMethods if isAccessible(place, method) &&
              !method.isConstructor && !method.hasModifierProperty("static")) {
        val phys = new PhysicalSignature(method, subst)
        val sig = new FullSignature(phys, new Suspension(() => {
          val psiRet = method.getReturnType
          val retType = if (psiRet == null) Unit else ScType.create(psiRet, method.getProject)
          subst.subst(retType)
        }), method, Some(clazz))
        map += ((sig, new Node(sig, subst)))
      }

    def processSyntheticScala(clazz: ScSyntheticClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      /*for (method <- clazz.syntheticMethods(place.map(_.getResolveScope).
        getOrElse(GlobalSearchScope.allScope(clazz.getProject)))) {
        val sig = new PhysicalSignature(method, subst)
        val ret = method.retType
        val fullSig = new FullSignature(sig, new Suspension[ScType](subst.subst(ret)), method, Some(clazz))
        map += (fullSig, new Node(fullSig, subst))
      }*/
    }

    def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) = {
      def addSignature(s: Signature, ret: => ScType, elem: NavigatablePsiElement) {
        val full = new FullSignature(s, new Suspension(() => ret), elem, Some(template))
        map += ((full, new Node(full, subst)))
      }

      for (member <- template.members) {
        member match {
          case _var: ScVariable if isAccessible(place, _var) =>
            for (dcl <- _var.declaredElements) {
              lazy val t = dcl.getType(TypingContext.empty).getOrElse(Any)
              addSignature(new Signature(dcl.name, Stream.empty, 0, subst), t, dcl)
              addSignature(new Signature(dcl.name + "_", Stream.apply(t), 1, subst), Unit, dcl)
            }
          case _val: ScValue if isAccessible(place, _val) =>
            for (dcl <- _val.declaredElements) {
              addSignature(new Signature(dcl.name, Stream.empty, 0, subst),
                dcl.getType(TypingContext.empty).getOrElse(Any), dcl)
            }
          case constr: ScPrimaryConstructor => {
            val isCase: Boolean = template match {
              case td: ScTypeDefinition if td.isCase => true
              case _ => false
            }
            val parameters = constr.parameters
            for (param <- parameters if isAccessible(place, param)) {
              if (!param.isEffectiveVal && place != None && place.get == template.extendsBlock) {
                //this is class parameter without val or var, it's like private val
                lazy val t = param.getType(TypingContext.empty).getOrElse(Any)
                addSignature(new Signature(param.name, Stream.empty, 0, subst), t, param)
              } else if (isAccessible(place, param)) {
                lazy val t = param.getType(TypingContext.empty).getOrElse(Any)
                addSignature(new Signature(param.name, Stream.empty, 0, subst), t, param)
                if (!param.isStable) addSignature(new Signature(param.name + "_", Stream.apply(t), 1, subst),
                  Unit, param)
              }
            }
          }
          case f: ScFunction if isAccessible(place, f) && !f.isConstructor =>
            addSignature(new PhysicalSignature(f, subst), subst.subst(f.returnType.getOrElse(Any)), f)
          case o: ScObject if (isAccessible(place, o)) =>
            addSignature(new Signature(o.name, Stream.empty, 0, subst),
              subst.subst(o.getType(TypingContext.empty).getOrElse(Any)), o)
          case _ =>
        }
      }
    }

    def processRefinement(cp: ScCompoundType, map: Map, place: Option[PsiElement]) {
      val subst = cp.subst
      def addSignature(s: Signature, ret: => ScType, elem: NavigatablePsiElement) {
        val full = new FullSignature(s, new Suspension(() => ret), elem, None)
        map += ((full, new Node(full, subst)))
      }
      for (decl <- cp.decls) {
        decl match {
          case fun: ScFunction if isAccessible(place, fun) => {
            val sign = new PhysicalSignature(fun, subst)
            addSignature(sign, fun.returnType.getOrElse(Any), fun)
          }
          case _var: ScVariable if isAccessible(place, _var) =>
            for (dcl <- _var.declaredElements) {
              lazy val t = dcl.getType(TypingContext.empty).getOrElse(Any)
              addSignature(new Signature(dcl.name, Stream.empty, 0, subst), t, dcl)
              addSignature(new Signature(dcl.name + "_", Stream.apply(t), 1, subst), Unit, dcl)
            }
          case _val: ScValue if isAccessible(place, _val) =>
            for (dcl <- _val.declaredElements) {
              addSignature(new Signature(dcl.name, Stream.empty, 0, subst), dcl.getType(TypingContext.empty).getOrElse(Any), dcl)
            }
          case _ =>
        }
      }
    }
  }

  import ValueNodes.{Map => VMap}, MethodNodes.{Map => MMap}, TypeNodes.{Map => TMap}, SignatureNodes.{Map => SMap}
  val valsKey: Key[CachedValue[(VMap, VMap)]] = Key.create("vals key")
  val methodsKey: Key[CachedValue[(MMap, MMap)]] = Key.create("methods key")
  val typesKey: Key[CachedValue[(TMap, TMap)]] = Key.create("types key")
  val signaturesKey: Key[CachedValue[(SMap, SMap)]] = Key.create("signatures key")

  def getVals(clazz: PsiClass): VMap = {
    get(clazz, valsKey, new MyProvider(clazz, {clazz: PsiClass => ValueNodes.build(clazz)}))._2
  }

  def getMethods(clazz: PsiClass): MMap = {
    get(clazz, methodsKey, new MyProvider(clazz, {clazz: PsiClass => MethodNodes.build(clazz)}))._2
  }

  def getTypes(clazz: PsiClass) = {
    get(clazz, typesKey, new MyProvider(clazz, {clazz: PsiClass => TypeNodes.build(clazz)}))._2
  }

  def getSignatures(c: PsiClass): SMap = {
    get(c, signaturesKey, new MyProvider(c, {c: PsiClass => SignatureNodes.build(c)}))._2
  }

  def getSuperVals(c: PsiClass) = {
    get(c, valsKey, new MyProvider(c, {c: PsiClass => ValueNodes.build(c)}))._1
  }

  def getSuperMethods(c: PsiClass) = {
    get(c, methodsKey, new MyProvider(c, {c: PsiClass => MethodNodes.build(c)}))._1
  }

  def getSuperTypes(c: PsiClass) = {
    get(c, typesKey, new MyProvider(c, {c: PsiClass => TypeNodes.build(c)}))._1
  }

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
      PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT)
  }

  def processDeclarations(clazz: PsiClass,
                          processor: PsiScopeProcessor,
                          state: ResolveState,
                          lastParent: PsiElement,
                          place: PsiElement): Boolean = {
    def methodsForJava: MethodNodes.Map = {
      if (!processor.isInstanceOf[BaseProcessor]) {
        clazz match {
          case td: ScTypeDefinition => {
            ScalaPsiUtil.getCompanionModule(td) match {
              case Some(clazz) => getMethods(clazz)
              case None => new MethodNodes.Map
            }
          }
          case _ => new MethodNodes.Map
        }
      } else new MethodNodes.Map
    }
    def syntheticMethods: Seq[(PhysicalSignature, MethodNodes.Node)] = {
      clazz match {
        case td: ScTemplateDefinition => td.syntheticMembers.map(fun => {
          val f = new PhysicalSignature(fun, ScSubstitutor.empty)
          (f, new MethodNodes.Node(f, ScSubstitutor.empty))
        })
        case _ => Seq.empty
      }
    }
    def valuesMap: ValueNodes.Map = {
      val map: ValueNodes.Map = getVals(clazz)
      if (!processor.isInstanceOf[BaseProcessor]) { //not a Scala
        clazz match {
          case td: ScTypeDefinition => {
            ScalaPsiUtil.getCompanionModule(td) match {
              case Some(clazz) => map ++= getVals(clazz)
              case None =>
            }
          }
          case _ =>
        }
      }
      map
    }

    if (!privateProcessDeclarations(processor, state, lastParent, place, valuesMap, getMethods(clazz), getTypes(clazz),
      clazz.isInstanceOf[ScObject], methodsForJava, syntheticMethods)) return false

    if (!(AnyRef.asClass(clazz.getProject).getOrElse(return true).processDeclarations(processor, state, lastParent, place) &&
            Any.asClass(clazz.getProject).getOrElse(return true).processDeclarations(processor, state, lastParent, place))) return false

    //fake enum methods
    val isJavaSourceEnum = !clazz.isInstanceOf[ClsClassImpl] && clazz.isEnum
    if (isJavaSourceEnum && shouldProcessMethods(processor)) {
      val elementFactory: PsiElementFactory = JavaPsiFacade.getInstance(clazz.getProject).getElementFactory
      //todo: cache like in PsiClassImpl
      val valuesMethod: PsiMethod = elementFactory.createMethodFromText("public static " + clazz.getName +
              "[] values() {}", clazz)
      val valueOfMethod: PsiMethod = elementFactory.createMethodFromText("public static " + clazz.getName +
              " valueOf(String name) throws IllegalArgumentException {}", clazz)
      val values = new LightMethod(clazz.getManager, valuesMethod, clazz)
      val valueOf = new LightMethod(clazz.getManager, valueOfMethod, clazz)
      if (!processor.execute(values, state)) return false
      if (!processor.execute(valueOf, state)) return false
    }
    true
  }

  def processSuperDeclarations(td: ScTemplateDefinition,
                               processor: PsiScopeProcessor,
                               state: ResolveState,
                               lastParent: PsiElement,
                               place: PsiElement): Boolean = {
    if (!privateProcessDeclarations(processor, state, lastParent, place, getSuperVals(td), getSuperMethods(td), getSuperTypes(td),
      td.isInstanceOf[ScObject])) return false

    if (!(AnyRef.asClass(td.getProject).getOrElse(return true).
      processDeclarations(processor, state, lastParent, place) &&
            Any.asClass(td.getProject).getOrElse(return true).
              processDeclarations(processor, state, lastParent, place))) return false
    true
  }

  def processDeclarations(comp: ScCompoundType,
                          processor: PsiScopeProcessor,
                          state: ResolveState,
                          lastParent: PsiElement,
                          place: PsiElement): Boolean = {
    privateProcessDeclarations(processor, state, lastParent, place, ValueNodes.build(comp)._2,
      MethodNodes.build(comp)._2, TypeNodes.build(comp)._2, false)
  }

  private def privateProcessDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement,
                                  vals: => ValueNodes.Map,
                                  methods: => MethodNodes.Map,
                                  types: => TypeNodes.Map,
                                  isObject: Boolean,
                                  methodsForJava: => MethodNodes.Map = new MethodNodes.Map,
                                  syntheticMethods: => Seq[(PhysicalSignature, MethodNodes.Node)] =
                                    Seq.empty): Boolean = {
    val substK = state.get(ScSubstitutor.key)
    val subst = if (substK == null) ScSubstitutor.empty else substK
    val nameHint = processor.getHint(NameHint.KEY)
    val name = if (nameHint == null) "" else nameHint.getName(state)
    val decodedName = if (name != null) NameTransformer.decode(name) else ""
    val isScalaProcessor = processor.isInstanceOf[BaseProcessor]
    val isNotScalaProcessor = !isScalaProcessor
    def checkName(s: String): Boolean = {
      if (name == null || name == "") true
      else {
        val s1 = if (s(0) == '`') s.drop(1).dropRight(1) else s
        NameTransformer.decode(s1) == decodedName
      }
    }
    def checkNameGetSetIs(s: String): Boolean = {
      if (name == null || name == "") true
      else {
        val decoded = NameTransformer.decode(s)
        val beanPropertyNames = Seq("is", "get", "set").map(_ + decoded.capitalize)
        beanPropertyNames.contains(decodedName)
      }
    }

    val processValsForScala = isScalaProcessor && shouldProcessVals(processor)
    val processValsForJava = isNotScalaProcessor && shouldProcessMethods(processor)

    if (processValsForScala || processValsForJava) {
      val iterator = vals.iterator
      while (iterator.hasNext) {
        val (_, n) = iterator.next()
        ProgressManager.checkCanceled()
        n.info match {
          case p: ScClassParameter if processValsForScala && !p.isVar && !p.isVal &&
            (checkName(p.getName) || checkNameGetSetIs(p.getName)) && !isNotScalaProcessor => {
            val clazz = PsiTreeUtil.getContextOfType(p, true, classOf[ScTemplateDefinition])
            if (clazz != null && clazz.isInstanceOf[ScClass] && !p.isEffectiveVal) {
              //this is member only for class scope
              if (PsiTreeUtil.isContextAncestor(clazz, place, false) && checkName(p.getName)) {
                //we can accept this member
                if (!processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
              } else {
                if (n.supers.length > 0 && !processor.execute(n.supers.apply(0).info, state.put(ScSubstitutor.key,
                  n.supers.apply(0).substitutor followed subst))) return false
              }
            } else if (!tail) return false
          }
          case _ => if (!tail) return false
        }
        def tail: Boolean = {
          if (processValsForScala && checkName(n.info.getName) &&
            !processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false

          //this is for Java: to find methods, which are vals in Scala
          if (processValsForJava) {
            n.info match {
              case t: ScTypedDefinition => {
                val context = ScalaPsiUtil.nameContext(t)
                context match {
                  case annotated: ScAnnotationsHolder =>
                    // Expose the get/set/is methods generated by scala.reflect.(Boolean)BeanProperty
                    if (!BeanProperty.processBeanPropertyDeclarations(annotated, context, processor, t, state))
                      return false
                  case _ =>
                }
                // Expose the accessor method for vals and vars to Java.
                context match {
                  case classParam: ScClassParameter if classParam.isEffectiveVal =>
                    if (!processor.execute(new FakePsiMethod(classParam, t.getName, Array.empty,
                      classParam.getType(TypingContext.empty).getOrElse(Any), classParam.hasModifierProperty _), state))
                      return false
                  case value: ScValue =>
                    if (!processor.execute(new FakePsiMethod(value, t.getName, Array.empty,
                      value.getType(TypingContext.empty).getOrElse(Any), value.hasModifierProperty _), state))
                      return false
                  case variable: ScVariable =>
                    if (!processor.execute(new FakePsiMethod(variable, t.getName, Array.empty,
                      variable.getType(TypingContext.empty).getOrElse(Any), variable.hasModifierProperty _), state))
                      return false
                  case _ =>
                }
              }
              case _ =>
            }
          }
          true
        }
      }
    }

    if (shouldProcessMethods(processor)) {
      def runIterator(iterator: Iterator[(MethodNodes.T, MethodNodes.Node)]): Option[Boolean] = {
        while (iterator.hasNext) {
          val (_, n) = iterator.next()
          ProgressManager.checkCanceled()
          val method = n.info.method
          if (checkName(method.getName)) {
            val substitutor = n.substitutor followed subst
            if (!processor.execute(method, state.put(ScSubstitutor.key, substitutor))) return Some(false)
          }
        }
        None
      }
      runIterator(methods.iterator) match {
        case Some(x) => return x
        case None =>
      }
      runIterator(methodsForJava.iterator) match {
        case Some(x) => return x
        case None =>
      }
      runIterator(syntheticMethods.iterator) match {
        case Some(x) => return x
        case None =>
      }
    }

    if (shouldProcessTypes(processor)) {
      val iterator = types.iterator
      while (iterator.hasNext) {
        val (_, n) = iterator.next()
        if (checkName(n.info.getName)) {
          ProgressManager.checkCanceled()
          if (!processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
        }
      }
    }
    //inner classes
    if (shouldProcessJavaInnerClasses(processor)) {
      val iterator = types.iterator
      while (iterator.hasNext) {
        val (_, n) = iterator.next()
        if (checkName(n.info.getName)) {
          ProgressManager.checkCanceled()
          if (n.info.isInstanceOf[ScTypeDefinition] &&
            !processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
        }
      }
    }

    true
  }

  import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._

  def shouldProcessVals(processor: PsiScopeProcessor) = processor match {
    case BaseProcessor(kinds) => (kinds contains VAR) || (kinds contains VAL) || (kinds contains OBJECT)
    case _ => {
      val hint: ElementClassHint = processor.getHint(ElementClassHint.KEY)
      hint == null || hint.shouldProcess(ElementClassHint.DeclarationKind.VARIABLE)
    }
  }

  def shouldProcessMethods(processor: PsiScopeProcessor) = processor match {
    case BaseProcessor(kinds) => kinds contains METHOD
    case _ => {
      val hint = processor.getHint(ElementClassHint.KEY)
      hint == null || hint.shouldProcess(ElementClassHint.DeclarationKind.METHOD)
    }
  }

  def shouldProcessTypes(processor: PsiScopeProcessor) = processor match {
    case BaseProcessor(kinds) => (kinds contains CLASS) || (kinds contains METHOD)
    case _ => false //important: do not process inner classes!
  }

  def shouldProcessJavaInnerClasses(processor: PsiScopeProcessor): Boolean = {
    if (processor.isInstanceOf[BaseProcessor]) return false
    val hint = processor.getHint(ElementClassHint.KEY)
    hint == null || hint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)
  }
}

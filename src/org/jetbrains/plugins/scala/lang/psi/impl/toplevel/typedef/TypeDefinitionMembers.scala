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
import com.intellij.openapi.progress.ProgressManager
import util._
import synthetic.ScSyntheticClass
import lang.resolve.ResolveUtils
import reflect.NameTransformer
import com.intellij.openapi.diagnostic.Logger
import types._
import caches.CachesUtil
import gnu.trove.THashMap
import lang.resolve.processor.{ImplicitProcessor, BaseProcessor}
import api.toplevel.{ScModifierListOwner, ScTypedDefinition}
import psi.ScalaPsiUtil.convertMemberName

/**
 * @author ven
 * @author alefas
 */
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

  object ParameterlessNodes extends MixinNodes {
    type T = Signature

    def equiv(s1: Signature, s2: Signature) = s1 equiv s2

    def computeHashCode(s: Signature) = s.hashCode

    def elemName(t: Signature) = t.name

    def isAbstract(s: Signature) = s match {
      case phys: PhysicalSignature => TypeDefinitionMembers.this.isAbstract(phys)
      case s: Signature if s.namedElement != None => s.namedElement.get match {
        case _: ScFieldId => true
        case f: PsiField if f.hasModifierProperty(PsiModifier.ABSTRACT) => true
        case _ => false
      }
      case _ => false
    }

    def isImplicit(t: Signature) = {
      t.namedElement match {
        case Some(s: ScModifierListOwner) => s.hasModifierProperty("implicit")
        case _ => false
      }
    }

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      for (method <- clazz.getMethods if isAccessible(place, method) &&
        !method.isConstructor && !method.hasModifierProperty("static") &&
        method.getParameterList.getParametersCount == 0) {
        val phys = new PhysicalSignature(method, subst)
        map addToMap (phys, new Node(phys, subst))
      }

      for (field <- clazz.getFields if (isAccessible(place, field) &&
        !field.hasModifierProperty("static"))) {
        val sig = new Signature(field.getName, Stream.empty, 0, subst, Some(field))
        map addToMap (sig, new Node(sig, subst))
      }
    }

    def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      def addSignature(s: Signature) {
        map addToMap (s, new Node(s, subst))
      }

      for (member <- template.members) {
        member match {
          case _var: ScVariable if isAccessible(place, _var) =>
            for (dcl <- _var.declaredElements) {
              addSignature(new Signature(dcl.name, Stream.empty, 0, subst, Some(dcl)))
            }
          case _val: ScValue if isAccessible(place, _val) =>
            for (dcl <- _val.declaredElements) {
              addSignature(new Signature(dcl.name, Stream.empty, 0, subst, Some(dcl)))
            }
          case constr: ScPrimaryConstructor => {
            val parameters = constr.parameters
            for (param <- parameters if isAccessible(place, param)) {
              if (!param.isEffectiveVal && place != None && place.get == template.extendsBlock) {
                //this is class parameter without val or var, it's like private val
                addSignature(new Signature(param.name, Stream.empty, 0, subst, Some(param)))
              } else {
                addSignature(new Signature(param.name, Stream.empty, 0, subst, Some(param)))
              }
            }
          }
          case f: ScFunction if isAccessible(place, f) && !f.isConstructor && f.parameters.length == 0 =>
            addSignature(new PhysicalSignature(f, subst))
          case c: ScClass if c.isCase && c.fakeCompanionModule != None && isAccessible(place, c) =>
            val o = c.fakeCompanionModule.get
            addSignature(new Signature(o.name, Stream.empty, 0, subst, Some(o)))
          case o: ScObject if (isAccessible(place, o)) =>
            addSignature(new Signature(o.name, Stream.empty, 0, subst, Some(o)))
          case _ =>
        }
      }

      template match {
        case obj: ScObject =>
          for (method <- obj.objectSyntheticMembers if method.getParameterList.getParametersCount == 0) {
            val sig = new PhysicalSignature(method, subst)
            addSignature(sig)
          }
        case _ =>
      }
    }

    def processRefinement(cp: ScCompoundType, map: Map, place: Option[PsiElement]) {
      val subst = cp.subst
      def addSignature(s: Signature) {
        map addToMap (s, new Node(s, subst))
      }
      for (decl <- cp.decls) {
        decl match {
          case fun: ScFunction if isAccessible(place, fun) && fun.parameters.isEmpty => {
            val sign = new PhysicalSignature(fun, subst)
            addSignature(sign)
          }
          case _var: ScVariable if isAccessible(place, _var) =>
            for (dcl <- _var.declaredElements) {
              addSignature(new Signature(dcl.name, Stream.empty, 0, subst, Some(dcl)))
            }
          case _val: ScValue if isAccessible(place, _val) =>
            for (dcl <- _val.declaredElements) {
              addSignature(new Signature(dcl.name, Stream.empty, 0, subst, Some(dcl)))
            }
          case _ =>
        }
      }
    }
  }

  import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

  object TypeNodes extends MixinNodes {
    type T = PsiNamedElement //class or type alias
    def equiv(t1: PsiNamedElement, t2: PsiNamedElement) = t1.getName == t2.getName

    def computeHashCode(t: PsiNamedElement) = t.getName.hashCode

    def elemName(t: PsiNamedElement) = t.getName

    def isAbstract(t: PsiNamedElement) = t match {
      case _: ScTypeAliasDeclaration => true
      case _ => false
    }

    def isImplicit(t: PsiNamedElement) = false

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      for (inner <- clazz.getInnerClasses if isAccessible(place, inner) &&
        !inner.hasModifierProperty("static")) {
        map addToMap (inner, new Node(inner, subst))
      }
    }

    def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      for (member <- template.members) {
        member match {
          case alias: ScTypeAlias if isAccessible(place, alias) => map addToMap (alias, new Node(alias, subst))
          case _: ScObject =>
          case td: ScTypeDefinition if isAccessible(place, td) => map addToMap (td, new Node(td, subst))
          case _ =>
        }
      }
    }

    def processRefinement(cp: ScCompoundType, map: Map, place: Option[PsiElement]) {
      for (alias <- cp.typeDecls if isAccessible(place, alias)) {
        map addToMap (alias, new Node(alias, cp.subst))
      }
    }
  }

  object SignatureNodes extends MixinNodes {
    type T = Signature

    def equiv(s1: Signature, s2: Signature) = s1 equiv s2

    def computeHashCode(s: Signature) = s.hashCode

    def elemName(t: Signature) = t.name

    def isAbstract(s: Signature) = s match {
      case phys: PhysicalSignature => TypeDefinitionMembers.this.isAbstract(phys)
      case s: Signature if s.namedElement != None => s.namedElement.get match {
        case _: ScFieldId => true
        case f: PsiField if f.hasModifierProperty(PsiModifier.ABSTRACT) => true
        case _ => false
      }
      case _ => false
    }

    def isImplicit(t: Signature) = {
      t.namedElement match {
        case Some(s: ScModifierListOwner) => s.hasModifierProperty("implicit")
        case _ => false
      }
    }

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      for (method <- clazz.getMethods if isAccessible(place, method) &&
        !method.isConstructor && !method.hasModifierProperty("static")) {
        val phys = new PhysicalSignature(method, subst)
        map addToMap (phys, new Node(phys, subst))
      }

      for (field <- clazz.getFields if (isAccessible(place, field) &&
        !field.hasModifierProperty("static"))) {
        val sig = new Signature(field.getName, Stream.empty, 0, subst, Some(field))
        map addToMap (sig, new Node(sig, subst))
      }
    }

    def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      def addSignature(s: Signature) {
        map addToMap (s, new Node(s, subst))
      }

      for (member <- template.members) {
        member match {
          case _var: ScVariable if isAccessible(place, _var) =>
            for (dcl <- _var.declaredElements) {
              lazy val t = dcl.getType(TypingContext.empty).getOrAny
              addSignature(new Signature(dcl.name, Stream.empty, 0, subst, Some(dcl)))
              addSignature(new Signature(dcl.name + "_=", ScalaPsiUtil.getSingletonStream(t), 1, subst, Some(dcl)))
            }
          case _val: ScValue if isAccessible(place, _val) =>
            for (dcl <- _val.declaredElements) {
              addSignature(new Signature(dcl.name, Stream.empty, 0, subst, Some(dcl)))
            }
          case constr: ScPrimaryConstructor => {
            val parameters = constr.parameters
            for (param <- parameters if isAccessible(place, param)) {
              if (!param.isEffectiveVal && place != None && place.get == template.extendsBlock) {
                //this is class parameter without val or var, it's like private val
                addSignature(new Signature(param.name, Stream.empty, 0, subst, Some(param)))
              } else if (isAccessible(place, param)) {
                lazy val t = param.getType(TypingContext.empty).getOrAny
                addSignature(new Signature(param.name, Stream.empty, 0, subst, Some(param)))
                if (!param.isStable) addSignature(new Signature(param.name + "_=", ScalaPsiUtil.getSingletonStream(t), 1, subst,
                  Some(param)))
              }
            }
          }
          case f: ScFunction if isAccessible(place, f) && !f.isConstructor =>
            addSignature(new PhysicalSignature(f, subst))
          case c: ScClass if c.isCase && c.fakeCompanionModule != None && isAccessible(place, c) =>
            val o = c.fakeCompanionModule.get
            addSignature(new Signature(o.name, Stream.empty, 0, subst, Some(o)))
          case o: ScObject if (isAccessible(place, o)) =>
            addSignature(new Signature(o.name, Stream.empty, 0, subst, Some(o)))
          case _ =>
        }
      }

      template match {
        case obj: ScObject =>
          for (method <- obj.objectSyntheticMembers) {
            val sig = new PhysicalSignature(method, subst)
            addSignature(sig)
          }
        case _ =>
      }
    }

    def processRefinement(cp: ScCompoundType, map: Map, place: Option[PsiElement]) {
      val subst = cp.subst
      def addSignature(s: Signature) {
        map addToMap (s, new Node(s, subst))
      }
      for (decl <- cp.decls) {
        decl match {
          case fun: ScFunction if isAccessible(place, fun) => {
            val sign = new PhysicalSignature(fun, subst)
            addSignature(sign)
          }
          case _var: ScVariable if isAccessible(place, _var) =>
            for (dcl <- _var.declaredElements) {
              lazy val t = dcl.getType(TypingContext.empty).getOrAny
              addSignature(new Signature(dcl.name, Stream.empty, 0, subst, Some(dcl)))
              addSignature(new Signature(dcl.name + "_=", ScalaPsiUtil.getSingletonStream(t), 1, subst, Some(dcl)))
            }
          case _val: ScValue if isAccessible(place, _val) =>
            for (dcl <- _val.declaredElements) {
              addSignature(new Signature(dcl.name, Stream.empty, 0, subst, Some(dcl)))
            }
          case _ =>
        }
      }
    }
  }

  import ParameterlessNodes.{Map => PMap}, TypeNodes.{Map => TMap}, SignatureNodes.{Map => SMap}
  val typesKey: Key[CachedValue[TMap]] = Key.create("types key")
  val signaturesKey: Key[CachedValue[SMap]] = Key.create("signatures key")
  val parameterlessKey: Key[CachedValue[PMap]] = Key.create("parameterless key")

  import CachesUtil.get
  import CachesUtil.MyProvider
  import PsiModificationTracker.{OUT_OF_CODE_BLOCK_MODIFICATION_COUNT => dep_item}

  def getParameterlessSignatures(clazz: PsiClass): PMap = {
    get(clazz, parameterlessKey, new MyProvider(clazz, {clazz: PsiClass => ParameterlessNodes.build(clazz)})(dep_item))
  }

  def getTypes(clazz: PsiClass): TMap = {
    get(clazz, typesKey, new MyProvider(clazz, {clazz: PsiClass => TypeNodes.build(clazz)})(dep_item))
  }

  def getSignatures(c: PsiClass): SMap = {
    get(c, signaturesKey, new MyProvider(c, {c: PsiClass => SignatureNodes.build(c)})(dep_item))
  }

  //todo: this method requires refactoring
  def processDeclarations(clazz: PsiClass,
                          processor: PsiScopeProcessor,
                          state: ResolveState,
                          lastParent: PsiElement,
                          place: PsiElement): Boolean = {
    def signaturesForJava: SignatureNodes.Map = {
      val map = new SignatureNodes.Map
      if (!processor.isInstanceOf[BaseProcessor]) {
        clazz match {
          case td: ScTypeDefinition => {
            ScalaPsiUtil.getCompanionModule(td) match {
              case Some(companionClass) => return getSignatures(companionClass)
              case None =>
            }
          }
          case _ =>
        }
      }
      map
    }

    def syntheticMethods: Seq[(Signature, SignatureNodes.Node)] = {
      clazz match {
        case td: ScTemplateDefinition => td.syntheticMembers.map(fun => {
          val f = new PhysicalSignature(fun, ScSubstitutor.empty)
          (f, new SignatureNodes.Node(f, ScSubstitutor.empty))
        })
        case _ => Seq.empty
      }
    }

    if (processor.isInstanceOf[ImplicitProcessor] && !clazz.isInstanceOf[ScTemplateDefinition]) return true

    if (!privateProcessDeclarations(processor, state, lastParent, place, getSignatures(clazz),
      getParameterlessSignatures(clazz), getTypes(clazz), false,
      clazz.isInstanceOf[ScObject], signaturesForJava, syntheticMethods)) return false

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
    if (!privateProcessDeclarations(processor, state, lastParent, place, getSignatures(td),
      getParameterlessSignatures(td), getTypes(td), true, td.isInstanceOf[ScObject])) return false

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
    if (!privateProcessDeclarations(processor, state, lastParent, place, SignatureNodes.build(comp),
      ParameterlessNodes.build(comp), TypeNodes.build(comp), false, false)) return false

    val project =
      if (lastParent != null) lastParent.getProject
      else if (place != null) place.getProject
      else return true
    if (!(AnyRef.asClass(project).getOrElse(return true).processDeclarations(processor, state, lastParent, place) &&
            Any.asClass(project).getOrElse(return true).processDeclarations(processor, state, lastParent, place)))
      return false

    true
  }

  private def privateProcessDeclarations(processor: PsiScopeProcessor,
                                         state: ResolveState,
                                         lastParent: PsiElement,
                                         place: PsiElement,
                                         signatures: => SignatureNodes.Map,
                                         parameterlessSignatures: => ParameterlessNodes.Map,
                                         types: => TypeNodes.Map,
                                         isSupers: Boolean,
                                         isObject: Boolean,
                                         signaturesForJava: => SignatureNodes.Map = new SignatureNodes.Map,
                                         syntheticMethods: => Seq[(Signature, SignatureNodes.Node)] = Seq.empty
                                         ): Boolean = {
    val substK = state.get(ScSubstitutor.key)
    val subst = if (substK == null) ScSubstitutor.empty else substK
    val nameHint = processor.getHint(NameHint.KEY)
    val name = if (nameHint == null) "" else nameHint.getName(state)
    val decodedName = if (name != null) convertMemberName(name) else ""
    val isScalaProcessor = processor.isInstanceOf[BaseProcessor]
    val isNotScalaProcessor = !isScalaProcessor
    def checkName(s: String): Boolean = {
      if (name == null || name == "") true
      else convertMemberName(s) == decodedName
    }
    def checkNameGetSetIs(s: String): Boolean = {
      if (name == null || name == "") true
      else {
        val decoded = NameTransformer.decode(s)
        val beanPropertyNames = Seq("is", "get", "set").map(_ + decoded.capitalize)
        beanPropertyNames.contains(decodedName)
      }
    }

    val processVals = shouldProcessVals(processor)
    val processMethods = shouldProcessMethods(processor)
    val processValsForScala = isScalaProcessor && processVals
    val processValsForJava = isNotScalaProcessor && processMethods
    val processOnlyStable = shouldProcessOnlyStable(processor)

    import collection.mutable.HashMap
    def process[T <: MixinNodes](signatures: T#Map): Boolean = {
      if (processValsForScala || processValsForJava || processMethods) {
        def runForValInfo(n: T#Node): Boolean = {
          val elem = n.info.asInstanceOf[Signature].namedElement match {
            case Some(named) => named
            case _ => return true
          }
          elem match {
            case p: ScClassParameter if processValsForScala && !p.isVar && !p.isVal &&
              (checkName(p.getName) || checkNameGetSetIs(p.getName)) && isScalaProcessor => {
              val clazz = PsiTreeUtil.getContextOfType(p, true, classOf[ScTemplateDefinition])
              if (clazz != null && clazz.isInstanceOf[ScClass] && !p.isEffectiveVal) {
                //this is member only for class scope
                if (PsiTreeUtil.isContextAncestor(clazz, place, false) && checkName(p.getName)) {
                  //we can accept this member
                  if (!processor.execute(elem, state.put(ScSubstitutor.key, n.substitutor followed subst)))
                    return false
                } else {
                  if (n.supers.length > 0 && n.supers.apply(0).info.asInstanceOf[Signature].namedElement != None &&
                    !processor.execute(n.supers.apply(0).info.asInstanceOf[Signature].namedElement.get,
                      state.put(ScSubstitutor.key, n.supers.apply(0).substitutor followed subst))) return false
                }
              } else if (!tail) return false
            }
            case _ => if (!tail) return false
          }
          def tail: Boolean = {
            if (processValsForScala && checkName(elem.getName) &&
              !processor.execute(elem, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false

            //this is for Java: to find methods, which are vals in Scala

            elem match {
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
                if (processValsForJava) {
                  context match {
                    case classParam: ScClassParameter if classParam.isEffectiveVal =>
                      if (!processor.execute(new FakePsiMethod(classParam, t.getName, Array.empty,
                        classParam.getType(TypingContext.empty).getOrAny, classParam.hasModifierProperty _), state))
                        return false
                    case value: ScValue =>
                      if (!processor.execute(new FakePsiMethod(value, t.getName, Array.empty,
                        value.getType(TypingContext.empty).getOrAny, value.hasModifierProperty _), state))
                        return false
                    case variable: ScVariable =>
                      if (!processor.execute(new FakePsiMethod(variable, t.getName, Array.empty,
                        variable.getType(TypingContext.empty).getOrAny, variable.hasModifierProperty _), state))
                        return false
                    case _ =>
                  }
                }
              }
              case _ =>
            }
            true
          }
          true
        }



        if (decodedName != "") {
          def checkList(s: String): Boolean = {
            val l = if (!isSupers) signatures.forName(s)._1 else signatures.forName(s)._2
            if (l != null) {
              val iterator = l.iterator
              while (iterator.hasNext) {
                val (_, n) = iterator.next()
                n.info match {
                  case phys: PhysicalSignature if processMethods =>
                    val method = phys.method
                    val substitutor = phys.substitutor followed subst
                    if (!processor.execute(method, state.put(ScSubstitutor.key, substitutor))) return false
                  case phys: PhysicalSignature => //do nothing
                  case _ if processValsForScala || processValsForJava =>
                    if (!runForValInfo(n)) return false
                  case _ => //do nothing
                }
              }
            }
            true
          }
          if (!checkList(decodedName)) return false

          if (processValsForScala || processValsForJava) {
            def checkPrefix(s: String): Boolean = {
              if (decodedName.startsWith(s)) {
                val n = decodedName.substring(s.length())
                if (n.length() > 0 && n(0).isUpper) {
                  val lowerName = n(0).toLower + n.substring(1)
                  checkList(lowerName)
                  if (lowerName != n)
                    checkList(n)
                }
              }
              true
            }
            if (!checkPrefix("is") || !checkPrefix("get") || !checkPrefix("set")) return false
          }
        } else if (processor.isInstanceOf[ImplicitProcessor]) {
          val implicits = signatures.forImplicits()
          val iterator = implicits.iterator
          while (iterator.hasNext) {
            val (sig, n) = iterator.next()
            ProgressManager.checkCanceled()
            sig match {
              case phys: PhysicalSignature if processMethods =>
                val method = phys.method
                if (checkName(method.getName)) {
                  val substitutor = n.substitutor followed subst
                  if (!processor.execute(method, state.put(ScSubstitutor.key, substitutor))) return false
                }
              case phys: PhysicalSignature => //do nothing
              case _ if processValsForScala || processValsForJava =>
                if (!runForValInfo(n)) return false
              case _ => //do nothing
            }
          }
        } else {
          val map = if (!isSupers) signatures.forAll()._1 else signatures.forAll()._2
          val valuesIterator = map.valuesIterator
          while (valuesIterator.hasNext) {
            val iterator = valuesIterator.next().iterator
            while (iterator.hasNext) {
              val (sig, n) = iterator.next()
              ProgressManager.checkCanceled()
              sig match {
                case phys: PhysicalSignature if processMethods =>
                  val method = phys.method
                  if (checkName(method.getName)) {
                    val substitutor = n.substitutor followed subst
                    if (!processor.execute(method, state.put(ScSubstitutor.key, substitutor))) return false
                  }
                case phys: PhysicalSignature => //do nothing
                case _ if processValsForScala || processValsForJava =>
                  if (!runForValInfo(n)) return false
                case _ => //do nothing
              }
            }
          }
        }

        def runIterator(iterator: Iterator[(SignatureNodes.T, SignatureNodes.Node)]): Option[Boolean] = {
          while (iterator.hasNext) {
            val (_, n) = iterator.next()
            ProgressManager.checkCanceled()
            val method = n.info match {
              case phys: PhysicalSignature => phys.method
              case _ => null
            }
            if (method != null && checkName(method.getName)) {
              val substitutor = n.substitutor followed subst
              if (!processor.execute(method, state.put(ScSubstitutor.key, substitutor))) return Some(false)
            }
          }
          None
        }

        if (processMethods) {
          val maps = signaturesForJava.forAll()._1
          val valuesIterator = maps.valuesIterator
          while (valuesIterator.hasNext) {
            val iterator = valuesIterator.next().iterator
            runIterator(iterator) match {
              case Some(x) => return x
              case None =>
            }
          }
          runIterator(syntheticMethods.iterator) match {
            case Some(x) => return x
            case None =>
          }
        }
      }
      true
    }

    if (processOnlyStable) {
      if (!process(parameterlessSignatures)) return false
    } else {
      if (!process(signatures)) return false
    }

    if (shouldProcessTypes(processor)) {
      if (decodedName != "") {
        val l: TypeNodes.NodesMap = if (!isSupers) types.forName(decodedName)._1 else types.forName(decodedName)._2
        val iterator = l.iterator
        while (iterator.hasNext) {
          val (_, n) = iterator.next()
          if (!processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
        }
      } else {
        val map = if (!isSupers) types.forAll()._1 else types.forAll()._2
        val valuesIterator = map.valuesIterator
        while (valuesIterator.hasNext) {
          val iterator = valuesIterator.next().iterator
          while (iterator.hasNext) {
            val (_, n) = iterator.next()
            if (checkName(n.info.getName)) {
              ProgressManager.checkCanceled()
              if (!processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
            }
          }
        }
      }
    }
    //inner classes
    if (shouldProcessJavaInnerClasses(processor)) {
      if (decodedName != "") {
        val l: TypeNodes.NodesMap = if (!isSupers) types.forName(decodedName)._1 else types.forName(decodedName)._2
        val iterator = l.iterator
        while (iterator.hasNext) {
          val (_, n) = iterator.next()
          if (n.info.isInstanceOf[ScTypeDefinition] &&
            !processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
        }
      } else {
        val map = if (!isSupers) types.forAll()._1 else types.forAll()._2
        val valuesIterator = map.valuesIterator
        while (valuesIterator.hasNext) {
          val iterator = valuesIterator.next().iterator
          while (iterator.hasNext) {
            val (_, n) = iterator.next()
            if (checkName(n.info.getName)) {
              ProgressManager.checkCanceled()
              if (n.info.isInstanceOf[ScTypeDefinition] &&
                !processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
            }
          }
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
    case _: ImplicitProcessor => false
    case BaseProcessor(kinds) => (kinds contains CLASS) || (kinds contains METHOD)
    case _ => false //important: do not process inner classes!
  }

  def shouldProcessJavaInnerClasses(processor: PsiScopeProcessor): Boolean = {
    if (processor.isInstanceOf[BaseProcessor]) return false
    val hint = processor.getHint(ElementClassHint.KEY)
    hint == null || hint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)
  }

  def shouldProcessOnlyStable(processor: PsiScopeProcessor): Boolean = {
    processor match {
      case BaseProcessor(kinds) =>
        !kinds.contains(METHOD) && !kinds.contains(VAR)
      case _ => false
    }
  }
}

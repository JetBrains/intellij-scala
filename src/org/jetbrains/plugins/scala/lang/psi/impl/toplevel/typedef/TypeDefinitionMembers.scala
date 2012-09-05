package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import api.statements.params.ScClassParameter
import com.intellij.psi._
import impl.compiled.ClsClassImpl
import impl.light.LightMethod
import scope.{NameHint, PsiScopeProcessor, ElementClassHint}
import api.toplevel.typedef._
import api.statements._
import types.result.TypingContext
import com.intellij.openapi.util.Key
import com.intellij.openapi.progress.ProgressManager
import util._
import reflect.NameTransformer
import com.intellij.openapi.diagnostic.Logger
import types._
import caches.CachesUtil
import lang.resolve.processor.{ImplicitProcessor, BaseProcessor}
import psi.ScalaPsiUtil.convertMemberName
import api.toplevel.{ScNamedElement, ScModifierListOwner, ScTypedDefinition}
import api.base.{ScAccessModifier, ScFieldId, ScPrimaryConstructor}
import extensions.toPsiNamedElementExt
import caches.CachesUtil.MyOptionalProvider
import api.ScalaFile

/**
 * @author ven
 * @author alefas
 */
object TypeDefinitionMembers {
  private val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers")

  def isBridge(place: Option[PsiElement], memb: PsiMember): Boolean = {
    memb match {
      case f: ScFunction if f.isBridge => false
      case _ => true
    }
  }

  def isAbstract(s: PhysicalSignature) = s.method match {
    case _: ScFunctionDeclaration => true
    case _: ScFunctionDefinition => false
    case m if m.hasModifierProperty(PsiModifier.ABSTRACT) => true
    case _ => false
  }

  object ParameterlessNodes extends MixinNodes {
    type T = Signature

    def equiv(s1: Signature, s2: Signature) = s1 equiv s2

    def computeHashCode(s: Signature) = s.hashCode

    def elemName(t: Signature) = t.name


    def same(t1: Signature, t2: Signature): Boolean = {
      if (t1.namedElement.isEmpty || t2.namedElement.isEmpty) {
        equiv(t1, t2)
      } else t1.namedElement.get eq t2.namedElement.get
    }

    def isPrivate(t: Signature): Boolean = {
      t.namedElement match {
        case Some(param: ScClassParameter) if !param.isEffectiveVal => true
        case Some(named: ScNamedElement) =>
          ScalaPsiUtil.nameContext(named) match {
            case s: ScModifierListOwner =>
              s.getModifierList.accessModifier match {
                case Some(a: ScAccessModifier) => a.isUnqualifiedPrivateOrThis
                case _ => false
              }
            case _ => false
          }
        case Some(n: PsiModifierListOwner) =>
          n.hasModifierProperty("private")
        case _ => false
      }
    }

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
        case Some(named: ScNamedElement) =>
          ScalaPsiUtil.nameContext(named) match {
            case s: ScModifierListOwner => s.hasModifierProperty("implicit")
            case _ => false
          }
        case _ => false
      }
    }

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      for (method <- clazz.getMethods if isBridge(place, method) &&
        !method.isConstructor && !method.hasModifierProperty("static") &&
        method.getParameterList.getParametersCount == 0) {
        val phys = new PhysicalSignature(method, subst)
        map addToMap (phys, new Node(phys, subst))
      }

      for (field <- clazz.getFields if (isBridge(place, field) &&
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
          case _var: ScVariable if isBridge(place, _var) =>
            for (dcl <- _var.declaredElements) {
              addSignature(new Signature(dcl.name, Stream.empty, 0, subst, Some(dcl)))
              dcl.nameContext match {
                case s: ScAnnotationsHolder =>
                  val beanProperty = ScalaPsiUtil.isBeanProperty(s, noResolve = true)
                  val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(s, noResolve = true)
                  if (beanProperty) {
                    addSignature(new Signature("get" + dcl.name.capitalize, Stream.empty, 0, subst, Some(dcl)))
                  } else if (booleanBeanProperty) {
                    addSignature(new Signature("is" + dcl.name.capitalize, Stream.empty, 0, subst, Some(dcl)))
                  }
                case _ =>
              }
            }
          case _val: ScValue if isBridge(place, _val) =>
            for (dcl <- _val.declaredElements) {
              addSignature(new Signature(dcl.name, Stream.empty, 0, subst, Some(dcl)))
              dcl.nameContext match {
                case s: ScAnnotationsHolder =>
                  val beanProperty = ScalaPsiUtil.isBeanProperty(s, noResolve = true)
                  val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(s, noResolve = true)
                  if (beanProperty) {
                    addSignature(new Signature("get" + dcl.name.capitalize, Stream.empty, 0, subst, Some(dcl)))
                  } else if (booleanBeanProperty) {
                    addSignature(new Signature("is" + dcl.name.capitalize, Stream.empty, 0, subst, Some(dcl)))
                  }
                case _ =>
              }
            }
          case constr: ScPrimaryConstructor => {
            val parameters = constr.parameters
            for (param <- parameters if isBridge(place, param)) {
               addSignature(new Signature(param.name, Stream.empty, 0, subst, Some(param)))
              val beanProperty = ScalaPsiUtil.isBeanProperty(param, noResolve = true)
              val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(param, noResolve = true)
              if (beanProperty) {
                addSignature(new Signature("get" + param.name.capitalize, Stream.empty, 0, subst, Some(param)))
              } else if (booleanBeanProperty) {
                addSignature(new Signature("is" + param.name.capitalize, Stream.empty, 0, subst, Some(param)))
              }
            }
          }
          case f: ScFunction if isBridge(place, f) && !f.isConstructor && f.parameters.length == 0 =>
            addSignature(new PhysicalSignature(f, subst))
          case c: ScClass if c.isCase && c.fakeCompanionModule != None && isBridge(place, c) =>
            val o = c.fakeCompanionModule.get
            addSignature(new Signature(o.name, Stream.empty, 0, subst, Some(o)))
          case o: ScObject if (isBridge(place, o)) =>
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
          case fun: ScFunction if isBridge(place, fun) && fun.parameters.isEmpty => {
            val sign = new PhysicalSignature(fun, subst)
            addSignature(sign)
          }
          case _var: ScVariable if isBridge(place, _var) =>
            for (dcl <- _var.declaredElements) {
              addSignature(new Signature(dcl.name, Stream.empty, 0, subst, Some(dcl)))
            }
          case _val: ScValue if isBridge(place, _val) =>
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
    def equiv(t1: PsiNamedElement, t2: PsiNamedElement) = t1.name == t2.name

    def computeHashCode(t: PsiNamedElement) = t.name.hashCode

    def elemName(t: PsiNamedElement) = t.name

    def isAbstract(t: PsiNamedElement) = t match {
      case _: ScTypeAliasDeclaration => true
      case _ => false
    }

    def isImplicit(t: PsiNamedElement) = false

    def same(t1: PsiNamedElement, t2: PsiNamedElement): Boolean = {
      t1 eq t2
    }

    def isPrivate(t: PsiNamedElement): Boolean = {
      t match {
        case n: ScModifierListOwner =>
          n.getModifierList.accessModifier match {
            case Some(a: ScAccessModifier) => a.isUnqualifiedPrivateOrThis
            case _ => false
          }
        case n: PsiModifierListOwner => n.hasModifierProperty("private")
        case _ => false
      }
    }

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      for (inner <- clazz.getInnerClasses if isBridge(place, inner) &&
        !inner.hasModifierProperty("static")) {
        map addToMap (inner, new Node(inner, subst))
      }
    }

    def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      for (member <- template.members) {
        member match {
          case alias: ScTypeAlias if isBridge(place, alias) => map addToMap (alias, new Node(alias, subst))
          case _: ScObject =>
          case td: ScTypeDefinition if isBridge(place, td) => map addToMap (td, new Node(td, subst))
          case _ =>
        }
      }
    }

    def processRefinement(cp: ScCompoundType, map: Map, place: Option[PsiElement]) {
      for (alias <- cp.typeDecls if isBridge(place, alias)) {
        map addToMap (alias, new Node(alias, cp.subst))
      }
    }
  }

  object SignatureNodes extends MixinNodes {
    type T = Signature

    def equiv(s1: Signature, s2: Signature) = s1 equiv s2

    def computeHashCode(s: Signature) = s.hashCode

    def elemName(t: Signature) = t.name

    def same(t1: Signature, t2: Signature): Boolean = {
      if (t1.namedElement.isEmpty || t2.namedElement.isEmpty) {
        equiv(t1, t2)
      } else t1.namedElement.get eq t2.namedElement.get
    }

    def isPrivate(t: Signature): Boolean = {
      t.namedElement match {
        case Some(c: ScClassParameter) if !c.isEffectiveVal => true
        case Some(named: ScNamedElement) =>
          ScalaPsiUtil.nameContext(named) match {
            case s: ScModifierListOwner =>
              s.getModifierList.accessModifier match {
                case Some(a: ScAccessModifier) => a.isUnqualifiedPrivateOrThis
                case _ => false
              }
            case _ => false
          }
        case Some(n: PsiModifierListOwner) =>
          n.hasModifierProperty("private")
        case _ => false
      }
    }

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
        case Some(named: ScNamedElement) =>
          ScalaPsiUtil.nameContext(named) match {
            case s: ScModifierListOwner => s.hasModifierProperty("implicit")
            case _ => false
          }
        case _ => false
      }
    }

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      for (method <- clazz.getMethods if isBridge(place, method) &&
        !method.isConstructor && !method.hasModifierProperty("static")) {
        val phys = new PhysicalSignature(method, subst)
        map addToMap (phys, new Node(phys, subst))
      }

      for (field <- clazz.getFields if (isBridge(place, field) &&
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
          case _var: ScVariable if isBridge(place, _var) =>
            for (dcl <- _var.declaredElements) {
              lazy val t = dcl.getType(TypingContext.empty).getOrAny
              addSignature(new Signature(dcl.name, Stream.empty, 0, subst, Some(dcl)))
              addSignature(new Signature(dcl.name + "_=", ScalaPsiUtil.getSingletonStream(t), 1, subst, Some(dcl)))
              dcl.nameContext match {
                case s: ScAnnotationsHolder =>
                  val beanProperty = ScalaPsiUtil.isBeanProperty(s, noResolve = true)
                  val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(s, noResolve = true)
                  if (beanProperty) {
                    addSignature(new Signature("get" + dcl.name.capitalize, Stream.empty, 0, subst, Some(dcl)))
                  } else if (booleanBeanProperty) {
                    addSignature(new Signature("is" + dcl.name.capitalize, Stream.empty, 0, subst, Some(dcl)))
                  }
                  if (beanProperty || booleanBeanProperty) {
                    addSignature(new Signature("set" + dcl.name.capitalize, ScalaPsiUtil.getSingletonStream(t), 1,
                      subst, Some(dcl)))
                  }
                case _ =>
              }
            }
          case _val: ScValue if isBridge(place, _val) =>
            for (dcl <- _val.declaredElements) {
              addSignature(new Signature(dcl.name, Stream.empty, 0, subst, Some(dcl)))
              dcl.nameContext match {
                case s: ScAnnotationsHolder =>
                  val beanProperty = ScalaPsiUtil.isBeanProperty(s, noResolve = true)
                  val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(s, noResolve = true)
                  if (beanProperty) {
                    addSignature(new Signature("get" + dcl.name.capitalize, Stream.empty, 0, subst, Some(dcl)))
                  } else if (booleanBeanProperty) {
                    addSignature(new Signature("is" + dcl.name.capitalize, Stream.empty, 0, subst, Some(dcl)))
                  }
                case _ =>
              }
            }
          case constr: ScPrimaryConstructor => {
            val parameters = constr.parameters
            for (param <- parameters if isBridge(place, param)) {
              lazy val t = param.getType(TypingContext.empty).getOrAny
              addSignature(new Signature(param.name, Stream.empty, 0, subst, Some(param)))
              if (!param.isStable) addSignature(new Signature(param.name + "_=", ScalaPsiUtil.getSingletonStream(t), 1, subst,
                Some(param)))
              val beanProperty = ScalaPsiUtil.isBeanProperty(param, noResolve = true)
              val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(param, noResolve = true)
              if (beanProperty) {
                addSignature(new Signature("get" + param.name.capitalize, Stream.empty, 0, subst, Some(param)))
                if (!param.isStable) {
                  addSignature(new Signature("set" + param.name.capitalize, ScalaPsiUtil.getSingletonStream(t), 1,
                    subst, Some(param)))
                }
              } else if (booleanBeanProperty) {
                addSignature(new Signature("is" + param.name.capitalize, Stream.empty, 0, subst, Some(param)))
                if (!param.isStable) {
                  addSignature(new Signature("set" + param.name.capitalize, ScalaPsiUtil.getSingletonStream(t), 1,
                    subst, Some(param)))
                }
              }
            }
          }
          case f: ScFunction if isBridge(place, f) && !f.isConstructor =>
            addSignature(new PhysicalSignature(f, subst))
          case c: ScClass =>
            if (c.isCase && c.fakeCompanionModule != None && isBridge(place, c)) {
              val o = c.fakeCompanionModule.get
              addSignature(new Signature(o.name, Stream.empty, 0, subst, Some(o)))
            }
            if (c.hasModifierProperty("implicit")) {
              c.getSyntheticImplicitMethod match {
                case Some(impl) =>
                  addSignature(new PhysicalSignature(impl, subst))
                case _ =>
              }
            }
          case o: ScObject if (isBridge(place, o)) =>
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
          case fun: ScFunction if isBridge(place, fun) => {
            val sign = new PhysicalSignature(fun, subst)
            addSignature(sign)
          }
          case _var: ScVariable if isBridge(place, _var) =>
            for (dcl <- _var.declaredElements) {
              lazy val t = dcl.getType(TypingContext.empty).getOrAny
              addSignature(new Signature(dcl.name, Stream.empty, 0, subst, Some(dcl)))
              addSignature(new Signature(dcl.name + "_=", ScalaPsiUtil.getSingletonStream(t), 1, subst, Some(dcl)))
            }
          case _val: ScValue if isBridge(place, _val) =>
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
    clazz match {
      case o: ScObject =>
        val qual = o.qualifiedName
        if (qual == "scala" || qual == "scala.Predef") {
          return o.getHardParameterlessSignatures
        }
      case _ =>
    }
    get(clazz, parameterlessKey, new MyOptionalProvider(clazz, {clazz: PsiClass => ParameterlessNodes.build(clazz)})(
      ScalaPsiUtil.getDependentItem(clazz)
    ))
  }

  def getTypes(clazz: PsiClass): TMap = {
    clazz match {
      case o: ScObject =>
        val qual = o.qualifiedName
        if (qual == "scala" || qual == "scala.Predef") {
          return o.getHardTypes
        }
      case _ =>
    }
    get(clazz, typesKey, new MyOptionalProvider(clazz, {clazz: PsiClass => TypeNodes.build(clazz)})(
      ScalaPsiUtil.getDependentItem(clazz)
    ))
  }

  def getSignatures(clazz: PsiClass): SMap = {
    clazz match {
      case o: ScObject =>
        val qual = o.qualifiedName
        if (qual == "scala" || qual == "scala.Predef") {
          return o.getHardSignatures
        }
      case _ =>
    }
    get(clazz, signaturesKey, new MyOptionalProvider(clazz, {c: PsiClass => SignatureNodes.build(c)})(
      ScalaPsiUtil.getDependentItem(clazz)
    ))
  }

  def getParameterlessSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType], place: PsiElement): PMap = {
    ScalaPsiManager.instance(place.getProject).getParameterlessSignatures(tp, compoundTypeThisType)
  }

  def getTypes(tp: ScCompoundType, compoundTypeThisType: Option[ScType], place: PsiElement): TMap = {
    ScalaPsiManager.instance(place.getProject).getTypes(tp, compoundTypeThisType)
  }

  def getSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType], place: PsiElement): SMap = {
    ScalaPsiManager.instance(place.getProject).getSignatures(tp, compoundTypeThisType)
  }

  def getSelfTypeSignatures(clazz: PsiClass): SMap = {
    clazz match {
      case td: ScTypeDefinition =>
        td.selfType match {
          case Some(selfType) =>
            val clazzType = td.getTypeWithProjections(TypingContext.empty).getOrAny
            Bounds.glb(selfType, clazzType) match {
              case c: ScCompoundType =>
                getSignatures(c, Some(clazzType), clazz)
              case _ =>
                getSignatures(clazz)
            }
          case _ =>
            getSignatures(clazz)
        }
      case _ => getSignatures(clazz)
    }
  }

  def getSelfTypeTypes(clazz: PsiClass): TMap = {
    clazz match {
      case td: ScTypeDefinition =>
        td.selfType match {
          case Some(selfType) =>
            val clazzType = td.getTypeWithProjections(TypingContext.empty).getOrAny
            Bounds.glb(selfType, clazzType) match {
              case c: ScCompoundType =>
                getTypes(c, Some(clazzType), clazz)
              case _ =>
                getTypes(clazz)
            }
          case _ =>
            getTypes(clazz)
        }
      case _ => getTypes(clazz)
    }
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
      getParameterlessSignatures(clazz), getTypes(clazz), isSupers = false,
      isObject = clazz.isInstanceOf[ScObject], signaturesForJava = signaturesForJava,
      syntheticMethods = syntheticMethods)) return false

    if (!(AnyRef.asClass(clazz.getProject).getOrElse(return true).processDeclarations(processor, state, lastParent, place) &&
            Any.asClass(clazz.getProject).getOrElse(return true).processDeclarations(processor, state, lastParent, place))) return false

    //fake enum methods
    val isJavaSourceEnum = !clazz.isInstanceOf[ClsClassImpl] && clazz.isEnum
    if (isJavaSourceEnum && shouldProcessMethods(processor)) {
      val elementFactory: PsiElementFactory = JavaPsiFacade.getInstance(clazz.getProject).getElementFactory
      //todo: cache like in PsiClassImpl
      val valuesMethod: PsiMethod = elementFactory.createMethodFromText("public static " + clazz.name +
              "[] values() {}", clazz)
      val valueOfMethod: PsiMethod = elementFactory.createMethodFromText("public static " + clazz.name +
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
      getParameterlessSignatures(td), getTypes(td), isSupers = true, isObject = td.isInstanceOf[ScObject])) return false

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
    val compoundTypeThisType = Option(state.get(BaseProcessor.COMPOUND_TYPE_THIS_TYPE_KEY)).getOrElse(None)
    if (!privateProcessDeclarations(processor, state, lastParent, place,
      getSignatures(comp, compoundTypeThisType, place), getParameterlessSignatures(comp, compoundTypeThisType, place),
      getTypes(comp, compoundTypeThisType, place), isSupers = false, isObject = false)) return false

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
    val processOnlyStable = shouldProcessOnlyStable(processor)

    def process[T <: MixinNodes](signatures: T#Map): Boolean = {
      if (processValsForScala || processMethods) {
        def runForValInfo(n: T#Node): Boolean = {
          val elem = n.info.asInstanceOf[Signature].namedElement match {
            case Some(named) => named
            case _ => return true
          }
          elem match {
            case p: ScClassParameter if processValsForScala && !p.isVar && !p.isVal &&
              (checkName(p.name) || checkNameGetSetIs(p.name)) && isScalaProcessor =>
              val clazz = PsiTreeUtil.getContextOfType(p, true, classOf[ScTemplateDefinition])
              if (clazz != null && clazz.isInstanceOf[ScClass] && !p.isEffectiveVal) {
                //this is member only for class scope
                if (PsiTreeUtil.isContextAncestor(clazz, place, false) && checkName(p.name)) {
                  //we can accept this member
                  if (!processor.execute(elem, state.put(ScSubstitutor.key, n.substitutor followed subst)))
                    return false
                } else {
                  if (n.supers.length > 0 && n.supers.apply(0).info.asInstanceOf[Signature].namedElement != None &&
                    !processor.execute(n.supers.apply(0).info.asInstanceOf[Signature].namedElement.get,
                      state.put(ScSubstitutor.key, n.supers.apply(0).substitutor followed subst))) return false
                }
              } else if (!tail) return false
            case _ => if (!tail) return false
          }
          def tail: Boolean = {
            if (processValsForScala && checkName(elem.name) &&
              !processor.execute(elem, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false

            if (checkNameGetSetIs(elem.name)) {
              elem match {
                case t: ScTypedDefinition =>
                  def process(method: PsiMethod): Boolean = {
                    if (processValsForScala &&
                      !processor.execute(method,
                        state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
                    true
                  }
                  if (decodedName.startsWith("set") && !process(t.getSetBeanMethod)) return false
                  if (decodedName.startsWith("get") && !process(t.getGetBeanMethod)) return false
                  if (decodedName.startsWith("is") && !process(t.getIsBeanMethod)) return false
                  if (decodedName.isEmpty) {
                    //completion processor    a
                    val beanMethodsIterator = t.getBeanMethods.iterator
                    while (beanMethodsIterator.hasNext) {
                      if (!process(beanMethodsIterator.next())) return false
                    }
                  }
                case _ =>
              }
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
                  case _ if processValsForScala =>
                    if (!runForValInfo(n)) return false
                  case _ => //do nothing
                }
              }
            }
            true
          }
          if (!checkList(decodedName)) return false
        } else if (processor.isInstanceOf[ImplicitProcessor]) {
          val implicits = signatures.forImplicits()
          val iterator = implicits.iterator
          while (iterator.hasNext) {
            val (sig, n) = iterator.next()
            ProgressManager.checkCanceled()
            sig match {
              case phys: PhysicalSignature if processMethods =>
                val method = phys.method
                if (checkName(method.name)) {
                  val substitutor = n.substitutor followed subst
                  if (!processor.execute(method, state.put(ScSubstitutor.key, substitutor))) return false
                }
              case phys: PhysicalSignature => //do nothing
              case _ if processValsForScala =>
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
                  if (checkName(method.name)) {
                    val substitutor = n.substitutor followed subst
                    if (!processor.execute(method, state.put(ScSubstitutor.key, substitutor))) return false
                  }
                case phys: PhysicalSignature => //do nothing
                case _ if processValsForScala =>
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
            if (method != null && checkName(method.name)) {
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
        val l: TypeNodes.AllNodes = if (!isSupers) types.forName(decodedName)._1 else types.forName(decodedName)._2
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
            if (checkName(n.info.name)) {
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
        val l: TypeNodes.AllNodes = if (!isSupers) types.forName(decodedName)._1 else types.forName(decodedName)._2
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
            if (checkName(n.info.name)) {
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

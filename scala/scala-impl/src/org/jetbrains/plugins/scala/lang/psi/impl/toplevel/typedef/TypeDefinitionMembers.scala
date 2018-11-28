package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.impl.light.LightMethod
import com.intellij.psi.scope.{ElementClassHint, NameHint, PsiScopeProcessor}
import com.intellij.psi.util._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAccessModifier, ScFieldId, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedPrefixReference
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.StdType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.processor._
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.UIFreezingGuard.withResponsibleUI

import scala.reflect.NameTransformer

/**
 * @author ven
 * @author alefas
 */
object TypeDefinitionMembers {

  private def nonBridge(place: Option[PsiElement], memb: PsiMember): Boolean = {
    memb match {
      case f: ScFunction if f.isBridge => false
      case _ => true
    }
  }

  private def isAbstractImpl(s: Signature): Boolean = s.namedElement match {
    case _: ScFunctionDeclaration => true
    case _: ScFunctionDefinition => false
    case _: ScFieldId => true
    case m: PsiModifierListOwner if m.hasModifierPropertyScala(PsiModifier.ABSTRACT) => true
    case _ => false
  }

  private def isPrivateImpl(named: PsiNamedElement): Boolean = {
    named match {
      case param: ScClassParameter if !param.isEffectiveVal => true
      case inNameContext(s: ScModifierListOwner) =>
        s.getModifierList.accessModifier match {
          case Some(a: ScAccessModifier) => a.isUnqualifiedPrivateOrThis
          case _ => false
        }
      case s: ScNamedElement => false
      case n: PsiModifierListOwner => n.hasModifierPropertyScala("private")
      case _ => false
    }
  }

  private def isSyntheticImpl(s: Signature) = s.namedElement match {
    case m: ScMember                => m.isSynthetic
    case inNameContext(m: ScMember) => m.isSynthetic
    case _                          => false
  }

  //noinspection ScalaWrongMethodsUsage
  private def isStaticJava(m: PsiMember): Boolean = m.hasModifierProperty("static")

  object ParameterlessNodes extends MixinNodes {
    type T = Signature

    def equiv(s1: Signature, s2: Signature): Boolean = s1 equiv s2

    def computeHashCode(s: Signature): Int = s.simpleHashCode

    def elemName(t: Signature): String = t.name

    override def isAbstract(t: Signature): Boolean = isAbstractImpl(t)

    def same(t1: Signature, t2: Signature): Boolean = {
      t1.namedElement eq t2.namedElement
    }

    def isPrivate(t: Signature): Boolean = isPrivateImpl(t.namedElement)

    def isSynthetic(s: Signature): Boolean = isSyntheticImpl(s)

    def isImplicit(t: Signature): Boolean = ScalaPsiUtil.isImplicit(t.namedElement)

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      implicit val ctx: ProjectContext = clazz

      for {
        method <- clazz.getMethods
        if nonBridge(place, method) && !method.isConstructor &&
          !isStaticJava(method) && method.getParameterList.getParametersCount == 0
      } {
        val phys = new PhysicalSignature(method, subst)
        map addToMap (phys, new Node(phys, subst))
      }

      for {
        field <- clazz.getFields
        if nonBridge(place, field) && !isStaticJava(field)
      } {
        val sig = Signature(field.getName, Seq.empty, subst, field)
        map addToMap (sig, new Node(sig, subst))
      }
    }

    def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      implicit val ctx: ProjectContext = template

      def addSignature(s: Signature) {
        map addToMap (s, new Node(s, subst))
      }

      if (template.qualifiedName == "scala.AnyVal") {
        //we need to add Object members
        val javaObject = ScalaPsiManager.instance(template.getProject).getCachedClass(template.resolveScope, "java.lang.Object")
        for (obj <- javaObject; method <- obj.getMethods) {
          method.getName match {
            case "hashCode" | "toString" =>
              addSignature(new PhysicalSignature(method, ScSubstitutor.empty))
            case _ =>
          }
        }
      }

      for (member <- template.members) {
        member match {
          case _var: ScVariable if nonBridge(place, _var) =>
            for (dcl <- _var.declaredElements) {
              addSignature(Signature(dcl, subst))
              dcl.nameContext match {
                case s: ScAnnotationsHolder =>
                  val beanProperty = ScalaPsiUtil.isBeanProperty(s, noResolve = true)
                  val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(s, noResolve = true)
                  if (beanProperty) {
                    addSignature(Signature.withoutParams("get" + dcl.name.capitalize, subst, dcl))
                  } else if (booleanBeanProperty) {
                    addSignature(Signature.withoutParams("is" + dcl.name.capitalize, subst, dcl))
                  }
                case _ =>
              }
            }
          case _val: ScValue if nonBridge(place, _val) =>
            for (dcl <- _val.declaredElements) {
              addSignature(Signature(dcl, subst))
              dcl.nameContext match {
                case s: ScAnnotationsHolder =>
                  val beanProperty = ScalaPsiUtil.isBeanProperty(s, noResolve = true)
                  val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(s, noResolve = true)
                  if (beanProperty) {
                    addSignature(Signature.withoutParams("get" + dcl.name.capitalize, subst, dcl))
                  } else if (booleanBeanProperty) {
                    addSignature(Signature.withoutParams("is" + dcl.name.capitalize, subst, dcl))
                  }
                case _ =>
              }
            }
          case constr: ScPrimaryConstructor =>
            val parameters = constr.parameters
            for (param <- parameters if nonBridge(place, param)) {
               addSignature(Signature(param, subst))
              val beanProperty = ScalaPsiUtil.isBeanProperty(param, noResolve = true)
              val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(param, noResolve = true)
              if (beanProperty) {
                addSignature(Signature.withoutParams("get" + param.name.capitalize, subst, param))
              } else if (booleanBeanProperty) {
                addSignature(Signature.withoutParams("is" + param.name.capitalize, subst, param))
              }
            }
          case f: ScFunction if nonBridge(place, f) && !f.isConstructor && f.parameters.isEmpty =>
            addSignature(new PhysicalSignature(f, subst))
          case o: ScObject if nonBridge(place, o) =>
            addSignature(Signature(o, subst))
          case c: ScTypeDefinition if c.fakeCompanionModule.isDefined && nonBridge(place, c) =>
            val o = c.fakeCompanionModule.get
            addSignature(Signature(o, subst))
          case _ =>
        }
      }

      for {
        method <- template.syntheticMethods
        if method.getParameterList.getParametersCount == 0
      } {
        val sig = new PhysicalSignature(method, subst)
        addSignature(sig)
      }

      for (td <- template.syntheticTypeDefinitions) {
        td match {
          case obj: ScObject => addSignature(Signature(obj, subst))
          case td: ScTypeDefinition =>
            td.fakeCompanionModule match {
              case Some(obj) => addSignature(Signature(obj, subst))
              case _ =>
            }
          case _ =>
        }
      }
    }

    def processRefinement(cp: ScCompoundType, map: Map, place: Option[PsiElement])
                         (implicit ctx: ProjectContext) {
      for ((sign, _) <- cp.signatureMap) {
        if (sign.paramLength.sum == 0 && (ScalaPsiUtil.nameContext(sign.namedElement) match {
          case m: PsiMember => nonBridge(place, m)
          case _ => false
        })) {
          map addToMap (sign, new Node(sign, sign.substitutor))
        }
      }
    }
  }

  import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

  object TypeNodes extends MixinNodes {
    type T = PsiNamedElement //class or type alias
    def equiv(t1: PsiNamedElement, t2: PsiNamedElement): Boolean = t1.name == t2.name

    def computeHashCode(t: PsiNamedElement): Int = t.name.hashCode

    def elemName(t: PsiNamedElement): String = t.name

    def isAbstract(t: PsiNamedElement): Boolean = t match {
      case _: ScTypeAliasDeclaration => true
      case _ => false
    }

    def isImplicit(t: PsiNamedElement) = false

    def same(t1: PsiNamedElement, t2: PsiNamedElement): Boolean = {
      t1 eq t2
    }

    def isPrivate(t: PsiNamedElement): Boolean = isPrivateImpl(t)

    def isSynthetic(t: PsiNamedElement): Boolean = false

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      implicit val ctx: ProjectContext = clazz

      for (inner <- clazz.getInnerClasses if nonBridge(place, inner) && !isStaticJava(inner)) {
        map addToMap (inner, new Node(inner, subst))
      }
    }

    def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      implicit val ctx: ProjectContext = template

      for (member <- template.members) {
        member match {
          case alias: ScTypeAlias if nonBridge(place, alias) => map addToMap (alias, new Node(alias, subst))
          case _: ScObject =>
          case td: ScTypeDefinition if nonBridge(place, td) => map addToMap (td, new Node(td, subst))
          case _ =>
        }
      }

      for (td <- template.syntheticTypeDefinitions if !td.isObject) {
        map addToMap (td, new Node(td, subst))
      }
    }

    def processRefinement(cp: ScCompoundType, map: Map, place: Option[PsiElement])
                         (implicit ctx: ProjectContext) {
      for ((name, TypeAliasSignature(_, _, _, _, _, alias)) <- cp.typesMap if nonBridge(place, alias)) {
        map addToMap (alias, new Node(alias, ScSubstitutor.empty))
      }
    }
  }

  object SignatureNodes extends MixinNodes {
    type T = Signature

    def equiv(s1: Signature, s2: Signature): Boolean = s1 equiv s2

    def computeHashCode(s: Signature): Int = s.simpleHashCode

    def elemName(t: Signature): String = t.name

    def same(t1: Signature, t2: Signature): Boolean = {
      t1.namedElement eq t2.namedElement
    }

    def isAbstract(t: Signature): Boolean = isAbstractImpl(t)

    def isPrivate(t: Signature): Boolean = isPrivateImpl(t.namedElement)

    def isSynthetic(s: Signature): Boolean = isSyntheticImpl(s)

    def isImplicit(t: Signature): Boolean = ScalaPsiUtil.isImplicit(t.namedElement)

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      implicit val ctx: ProjectContext = clazz

      for {
        method <- clazz.getMethods
        if nonBridge(place, method) && !method.isConstructor && !isStaticJava(method)
      } {
        val phys = new PhysicalSignature(method, subst)
        map addToMap (phys, new Node(phys, subst))
      }

      for {
        field <- clazz.getFields
        if nonBridge(place, field) && !isStaticJava(field)
      } {
        val sig = Signature.withoutParams(field.getName, subst, field)
        map addToMap (sig, new Node(sig, subst))
      }
    }

    def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      implicit val ctx: ProjectContext = template

      def addSignature(s: Signature) {
        map addToMap (s, new Node(s, subst))
      }

      if (template.qualifiedName == "scala.AnyVal") {
        //we need to add Object members
        val javaObject = ScalaPsiManager.instance.getCachedClass(template.resolveScope, "java.lang.Object")

        for (obj <- javaObject; method <- obj.getMethods) {
          method.getName match {
            case "equals" | "hashCode" | "toString" =>
              addSignature(new PhysicalSignature(method, ScSubstitutor.empty))
            case _ =>
          }
        }
      }

      for (member <- template.members) {
        member match {
          case _var: ScVariable if nonBridge(place, _var) =>
            for (dcl <- _var.declaredElements) {
              lazy val t = dcl.`type`().getOrAny
              addSignature(Signature(dcl, subst))
              addSignature(Signature.setter(dcl, subst))
              dcl.nameContext match {
                case s: ScAnnotationsHolder =>
                  val beanProperty = ScalaPsiUtil.isBeanProperty(s, noResolve = true)
                  val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(s, noResolve = true)
                  if (beanProperty) {
                    addSignature(Signature.withoutParams("get" + dcl.name.capitalize, subst, dcl))
                  } else if (booleanBeanProperty) {
                    addSignature(Signature.withoutParams("is" + dcl.name.capitalize, subst, dcl))
                  }
                  if (beanProperty || booleanBeanProperty) {
                    addSignature(Signature("set" + dcl.name.capitalize, Seq(() => t), subst, dcl))
                  }
                case _ =>
              }
            }
          case _val: ScValue if nonBridge(place, _val) =>
            for (dcl <- _val.declaredElements) {
              addSignature(Signature(dcl, subst))
              dcl.nameContext match {
                case s: ScAnnotationsHolder =>
                  val beanProperty = ScalaPsiUtil.isBeanProperty(s, noResolve = true)
                  val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(s, noResolve = true)
                  if (beanProperty) {
                    addSignature(Signature.withoutParams("get" + dcl.name.capitalize, subst, dcl))
                  } else if (booleanBeanProperty) {
                    addSignature(Signature.withoutParams("is" + dcl.name.capitalize, subst, dcl))
                  }
                case _ =>
              }
            }
          case constr: ScPrimaryConstructor =>
            val parameters = constr.parameters
            for (param <- parameters if nonBridge(place, param)) {
              lazy val t = param.`type`().getOrAny
              addSignature(Signature(param, subst))
              if (!param.isStable) addSignature(Signature(param.name + "_=", Seq(() => t), subst, param))
              val beanProperty = ScalaPsiUtil.isBeanProperty(param, noResolve = true)
              val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(param, noResolve = true)
              if (beanProperty) {
                addSignature(Signature.withoutParams("get" + param.name.capitalize, subst, param))
                if (!param.isStable) {
                  addSignature(Signature("set" + param.name.capitalize, Seq(() => t), subst, param))
                }
              } else if (booleanBeanProperty) {
                addSignature(Signature.withoutParams("is" + param.name.capitalize, subst, param))
                if (!param.isStable) {
                  addSignature(Signature("set" + param.name.capitalize, Seq(() => t), subst, param))
                }
              }
            }
          case f: ScFunction if nonBridge(place, f) && !f.isConstructor =>
            addSignature(new PhysicalSignature(f, subst))
          case o: ScObject if nonBridge(place, o) =>
            addSignature(Signature(o, subst))
          case c: ScTypeDefinition =>
            if (c.fakeCompanionModule.isDefined && nonBridge(place, c)) {
              val o = c.fakeCompanionModule.get
              addSignature(Signature(o, subst))
            }
            c match {
              case c: ScClass if c.hasModifierProperty("implicit") =>
                c.getSyntheticImplicitMethod match {
                  case Some(impl) =>
                    addSignature(new PhysicalSignature(impl, subst))
                  case _ =>
                }
              case _ =>
            }
          case _ =>
        }
      }

      for (method <- template.syntheticMethods) {
        val sig = new PhysicalSignature(method, subst)
        addSignature(sig)
      }

      for (td <- template.syntheticTypeDefinitions) {
        td match {
          case obj: ScObject => addSignature(Signature(obj, subst))
          case td: ScTypeDefinition =>
            td.fakeCompanionModule match {
              case Some(obj) => addSignature(Signature(obj, subst))
              case _ =>
            }
          case _ =>
        }
      }
    }

    def processRefinement(cp: ScCompoundType, map: Map, place: Option[PsiElement])
                         (implicit ctx: ProjectContext) {
      for ((sign, _) <- cp.signatureMap) {
        if (ScalaPsiUtil.nameContext(sign.namedElement) match {
          case m: PsiMember => nonBridge(place, m)
          case _ => false
        }) {
          map addToMap (sign, new Node(sign, sign.substitutor))
        }
      }
    }

    def forAllSignatureNodes(c: PsiClass)(action: Node => Unit): Unit = {
      withResponsibleUI {
        for {
          signature <- TypeDefinitionMembers.getSignatures(c).allFirstSeq()
          (_, node) <- signature
        } action(node)
      }
    }
  }

  import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.ParameterlessNodes.{Map => PMap}
  import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.SignatureNodes.{Map => SMap}
  import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.TypeNodes.{Map => TMap}

  def getSignatures(clazz: PsiClass): SMap              = ScalaPsiManager.instance(clazz).SignatureNodesCache.cachedMap(clazz)
  def getParameterlessSignatures(clazz: PsiClass): PMap = ScalaPsiManager.instance(clazz).ParameterlessNodesCache.cachedMap(clazz)
  def getTypes(clazz: PsiClass): TMap                   = ScalaPsiManager.instance(clazz).TypeNodesCache.cachedMap(clazz)

  def getSignatures(clazz: PsiClass, place: Option[PsiElement] = None): SMap = {
    val ans = getSignatures(clazz)
    place.foreach {
      case _: ScInterpolatedPrefixReference =>
        val allowedNames = ans.publicNames
        val eb = clazz match {
          case td: ScTemplateDefinition => Some(td.extendsBlock)
          case _ => clazz.getChildren.collectFirst({case e: ScExtendsBlock => e})
        }
        eb.foreach { n =>
          val children = n.getFirstChild.getChildren
          for (c <- children) {
            c match {
              case o: ScObject =>
                if (allowedNames.contains(o.name)) {
                  val add = getSignatures(o)
                  ans.addPublicsFrom(add)
                }
              case c: ScClass =>
                if (allowedNames.contains(c.name)) {
                  val add = getSignatures(c)
                  ans.addPublicsFrom(add)
                }
              case _ =>
            }
          }
        }
      case _ =>
    }
    ans
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
            val clazzType = td.getTypeWithProjections().getOrAny
            selfType.glb(clazzType) match {
              case c: ScCompoundType =>
                getSignatures(c, Some(clazzType), clazz)
              case tp =>
                val cl = tp.extractClass.getOrElse(clazz)
                getSignatures(cl)
            }
          case _ =>
            getSignatures(clazz)
        }
      case _ => getSignatures(clazz)
    }
  }

  def getSelfTypeTypes(clazz: PsiClass): TMap = {
    implicit val ctx: ProjectContext = clazz

    clazz match {
      case td: ScTypeDefinition =>
        td.selfType match {
          case Some(selfType) =>
            val clazzType = td.getTypeWithProjections().getOrAny
            selfType.glb(clazzType) match {
              case c: ScCompoundType =>
                getTypes(c, Some(clazzType), clazz)
              case tp =>
                val cl = tp.extractClass.getOrElse(clazz)
                getTypes(cl)
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
    implicit val projectContext: ProjectContext = clazz.projectContext

    if (BaseProcessor.isImplicitProcessor(processor) && !clazz.isInstanceOf[ScTemplateDefinition]) return true

    if (!privateProcessDeclarations(processor, state, lastParent, place,
      () => getSignatures(clazz, Option(place)),
      () => getParameterlessSignatures(clazz),
      () => getTypes(clazz),
      isSupers = false,
      signaturesForJava = () => signaturesForJava(clazz, processor))) return false

    if (!processSyntheticAnyRefAndAny(processor, state, lastParent, place)) return false

    if (shouldProcessMethods(processor) && !processEnum(clazz, processor.execute(_, state))) return false
    true
  }

  def processSuperDeclarations(td: ScTemplateDefinition,
                               processor: PsiScopeProcessor,
                               state: ResolveState,
                               lastParent: PsiElement,
                               place: PsiElement): Boolean = {
    import td.projectContext

    if (!privateProcessDeclarations(processor, state, lastParent, place,
      () => getSignatures(td),
      () => getParameterlessSignatures(td),
      () => getTypes(td),
      isSupers = true)) return false

    if (!processSyntheticAnyRefAndAny(processor, state, lastParent, place)) return false

    true
  }

  def processDeclarations(comp: ScCompoundType,
                          processor: PsiScopeProcessor,
                          state: ResolveState,
                          lastParent: PsiElement,
                          place: PsiElement): Boolean = {
    import comp.projectContext

    val compoundTypeThisType = Option(state.get(BaseProcessor.COMPOUND_TYPE_THIS_TYPE_KEY)).flatten

    if (!privateProcessDeclarations(processor, state, lastParent, place,
      () => getSignatures(comp, compoundTypeThisType, place),
      () => getParameterlessSignatures(comp, compoundTypeThisType, place),
      () => getTypes(comp, compoundTypeThisType, place),
      isSupers = false)) return false

    if (!processSyntheticAnyRefAndAny(processor, state, lastParent, place)) return false

    true
  }

  class Lazy[T](private var thunk: () => T) {
    private var value: T = _
    def apply(): T = {
      if (value == null) {
        value = thunk()
        require(value != null)
        thunk = null // release memory captured by the thunk
      }
      value
    }
  }

  object Lazy {
    import scala.language.implicitConversions

    implicit def any2lazy[T](t: () => T): Lazy[T] = new Lazy(t)
  }

  private def privateProcessDeclarations(processor: PsiScopeProcessor,
                                         state: ResolveState,
                                         lastParent: PsiElement,
                                         place: PsiElement,
                                         signatures: Lazy[SignatureNodes.Map],
                                         parameterlessSignatures:  Lazy[ParameterlessNodes.Map],
                                         types: Lazy[TypeNodes.Map],
                                         isSupers: Boolean,
                                         signaturesForJava: Lazy[SignatureNodes.Map] = () => new SignatureNodes.Map
                                         ): Boolean = {
    val subst = Option(state.get(ScSubstitutor.key)).getOrElse(ScSubstitutor.empty)
    val nameHint = processor.getHint(NameHint.KEY)
    val name = if (nameHint == null) "" else nameHint.getName(state)

    val decodedName = if (name != null) ScalaNamesUtil.clean(name) else ""
    val isScalaProcessor = processor.isInstanceOf[BaseProcessor]
    def checkName(s: String): Boolean = {
      if (name == null || name == "") true
      else ScalaNamesUtil.clean(s) == decodedName
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
    val processMethodRefs = shouldProcessMethodRefs(processor)
    val processValsForScala = isScalaProcessor && processVals
    val processOnlyStable = shouldProcessOnlyStable(processor)

    def addElement(named: PsiNamedElement, nodeSubstitutor: ScSubstitutor): Boolean =
      processor.execute(named, state.put(ScSubstitutor.key, nodeSubstitutor.followed(subst)))

    def process[T <: MixinNodes](signatures: T#Map): Boolean = {
      if (processValsForScala || processMethods) {
        def runForValInfo(n: T#Node): Boolean = {
          val signature = n.info.asInstanceOf[Signature]
          val elem = signature.namedElement
          elem match {
            case p: ScClassParameter if processValsForScala && !p.isVar && !p.isVal &&
              (checkName(p.name) || checkNameGetSetIs(p.name)) && isScalaProcessor =>
              val clazz = PsiTreeUtil.getContextOfType(p, true, classOf[ScTemplateDefinition])
              if (clazz != null && clazz.isInstanceOf[ScClass] && !p.isEffectiveVal) {
                //this is member only for class scope
                if (PsiTreeUtil.isContextAncestor(clazz, place, false) && checkName(p.name)) {
                  //we can accept this member
                  if (!addElement(elem, n.substitutor))
                    return false
                } else {
                  if (n.supers.nonEmpty) {
                    val head = n.supers.head
                    val named = head.info.asInstanceOf[Signature].namedElement
                    val substitutor = head.substitutor
                    if (!addElement(named, substitutor)) return false
                  }
                }
              } else if (!tail) return false
            case _ => if (!tail) return false
          }
          def tail: Boolean = {
            if (processValsForScala && checkName(elem.name) &&
              !addElement(elem, n.substitutor)) return false

            if (name == null || name.isEmpty || checkName(s"${elem.name}_=")) {
              elem match {
                case t: ScTypedDefinition if t.isVar && signature.name.endsWith("_=") =>
                  if (processValsForScala && !addElement(t.getUnderEqualsMethod, n.substitutor)) return false
                case _ =>
              }
            }

            if (checkNameGetSetIs(elem.name)) {
              elem match {
                case t: ScTypedDefinition if processValsForScala =>
                  def process(method: PsiMethod): Boolean = addElement(method, n.substitutor)

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

        def addSignature(sig: T#T, n: T#Node): Boolean = {
          import scala.language.existentials

          ProgressManager.checkCanceled()
          def addMethod(method: PsiNamedElement): Boolean = {
            if (checkName(method.name) && !addElement(method, n.substitutor)) return false
            true
          }
          sig match {
            case phys: PhysicalSignature if processMethods => if (!addMethod(phys.method)) return false
            case _: PhysicalSignature => //do nothing
            case s: Signature if processMethods && s.namedElement.isInstanceOf[PsiMethod] =>
              //this is compound type case
              if (!addMethod(s.namedElement)) return false
            case _ if processValsForScala => if (!runForValInfo(n)) return false
            case _ => //do nothing
          }
          true
        }

        if (decodedName != "") {
          def checkList(s: String): Boolean = {
            val l = if (!isSupers) signatures.forName(s)._1 else signatures.forName(s)._2
            if (l != null) {
              val iterator: Iterator[(T#T, T#Node)] = l.iterator
              while (iterator.hasNext) {
                val (_, n) = iterator.next()
                def addMethod(method: PsiNamedElement): Boolean = addElement(method, n.substitutor)

                n.info match {
                  case phys: PhysicalSignature if processMethods => if (!addMethod(phys.method)) return false
                  case _: PhysicalSignature => //do nothing
                  case s: Signature if processMethods && s.namedElement.isInstanceOf[PsiMethod] =>
                    //this is compound type case
                    if (!addMethod(s.namedElement)) return false
                  case _ if processValsForScala =>
                    if (!runForValInfo(n)) return false
                  case _ => //do nothing
                }
              }
            }
            true
          }
          if (!checkList(decodedName)) return false
        } else if (BaseProcessor.isImplicitProcessor(processor)) {
          val implicits = signatures.forImplicits()
          val iterator: Iterator[(T#T, T#Node)] = implicits.iterator
          while (iterator.hasNext) {
            val (sig, n) = iterator.next()
            if (!addSignature(sig, n)) return false
          }
        } else {
          val map = if (!isSupers) signatures.allFirstSeq() else signatures.allSecondSeq()
          val valuesIterator = map.iterator
          while (valuesIterator.hasNext) {
            val iterator: Iterator[(T#T, T#Node)] = valuesIterator.next().iterator
            while (iterator.hasNext) {
              val (sig, n) = iterator.next()
              if (!addSignature(sig, n)) return false
            }
          }
        }

        def processAll(iterator: Iterator[(Signature, SignatureNodes.Node)]): Boolean = {
          while (iterator.hasNext) {
            val (_, n) = iterator.next()
            ProgressManager.checkCanceled()
            val method = n.info match {
              case phys: PhysicalSignature => phys.method
              case _ => null
            }
            if (method != null && checkName(method.name) && !addElement(method, n.substitutor))
              return false
          }
          true
        }

        if (processMethods) {
          val maps = signaturesForJava().allFirstSeq()
          val valuesIterator = maps.iterator
          while (valuesIterator.hasNext) {
            val iterator = valuesIterator.next().iterator
            if (!processAll(iterator)) return false
          }
        }
      }
      true
    }

    if (shouldProcessTypes(processor)) {
      if (decodedName != "") {
        val l: TypeNodes.AllNodes = if (!isSupers) types().forName(decodedName)._1 else types().forName(decodedName)._2
        val iterator = l.iterator
        while (iterator.hasNext) {
          val (_, n) = iterator.next()
          if (!addElement(n.info, n.substitutor)) return false
        }
      } else {
        val map = if (!isSupers) types().allFirstSeq() else types().allSecondSeq()
        val valuesIterator = map.iterator
        while (valuesIterator.hasNext) {
          val iterator = valuesIterator.next().iterator
          while (iterator.hasNext) {
            val (_, n) = iterator.next()
            if (checkName(n.info.name)) {
              ProgressManager.checkCanceled()
              if (!addElement(n.info, n.substitutor)) return false
            }
          }
        }
      }
    }

    if (processMethodRefs) {
      if (processOnlyStable) {
        if (!process(parameterlessSignatures())) return false
      } else {
        if (!process(signatures())) return false
      }
    }

    //inner classes
    if (shouldProcessJavaInnerClasses(processor)) {
      if (decodedName != "") {
        val l: TypeNodes.AllNodes = if (!isSupers) types().forName(decodedName)._1 else types().forName(decodedName)._2
        val iterator = l.iterator
        while (iterator.hasNext) {
          val (_, n) = iterator.next()
          if (n.info.isInstanceOf[ScTypeDefinition] && !addElement(n.info, n.substitutor)) return false
        }
      } else {
        val map = if (!isSupers) types().allFirstSeq() else types().allSecondSeq()
        val valuesIterator = map.iterator
        while (valuesIterator.hasNext) {
          val iterator = valuesIterator.next().iterator
          while (iterator.hasNext) {
            val (_, n) = iterator.next()
            if (checkName(n.info.name)) {
              ProgressManager.checkCanceled()
              if (n.info.isInstanceOf[ScTypeDefinition] && !addElement(n.info, n.substitutor)) return false
            }
          }
        }
      }
    }

    true
  }

  import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._

  def shouldProcessVals(processor: PsiScopeProcessor): Boolean = processor match {
    case BaseProcessor(kinds) => (kinds contains VAR) || (kinds contains VAL) || (kinds contains OBJECT)
    case _ =>
      val hint: ElementClassHint = processor.getHint(ElementClassHint.KEY)
      hint == null || hint.shouldProcess(ElementClassHint.DeclarationKind.VARIABLE)
  }

  def shouldProcessMethods(processor: PsiScopeProcessor): Boolean = processor match {
    case BaseProcessor(kinds) => kinds contains METHOD
    case _ =>
      val hint = processor.getHint(ElementClassHint.KEY)
      hint == null || hint.shouldProcess(ElementClassHint.DeclarationKind.METHOD)
  }

  def shouldProcessMethodRefs(processor: PsiScopeProcessor): Boolean = processor match {
    case BaseProcessor(kinds) => (kinds contains METHOD) || (kinds contains VAR) || (kinds contains VAL)
    case _ => true
  }

  def shouldProcessTypes(processor: PsiScopeProcessor): Boolean = processor match {
    case b: BaseProcessor if b.isImplicitProcessor => false
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

  def processEnum(clazz: PsiClass, process: PsiMethod => Boolean): Boolean = {
    var containsValues = false
    if (clazz.isEnum && !clazz.isInstanceOf[ScTemplateDefinition]) {
      containsValues = clazz.getMethods.exists {
        method =>
          method.getName == "values" && method.getParameterList.getParametersCount == 0 && isStaticJava(method)
      }
    }

    if (!containsValues && clazz.isEnum) {
      val elementFactory: PsiElementFactory = JavaPsiFacade.getInstance(clazz.getProject).getElementFactory
      //todo: cache like in PsiClassImpl
      val valuesMethod: PsiMethod = elementFactory.createMethodFromText("public static " + clazz.name +
        "[] values() {}", clazz)
      val valueOfMethod: PsiMethod = elementFactory.createMethodFromText("public static " + clazz.name +
        " valueOf(java.lang.String name) throws java.lang.IllegalArgumentException {}", clazz)
      val values = new LightMethod(clazz.getManager, valuesMethod, clazz)
      val valueOf = new LightMethod(clazz.getManager, valueOfMethod, clazz)
      if (!process(values)) return false
      if (!process(valueOf)) return false
    }
    true
  }

  private def signaturesForJava(clazz: PsiClass, processor: PsiScopeProcessor): SignatureNodes.Map = {
    val map = new SignatureNodes.Map
    if (!processor.isInstanceOf[BaseProcessor]) {
      clazz match {
        case td: ScTypeDefinition =>
          ScalaPsiUtil.getCompanionModule(td) match {
            case Some(companionClass) => return getSignatures(companionClass)
            case None =>
          }
        case _ =>
      }
    }
    map
  }

  private def processSyntheticAnyRefAndAny(processor: PsiScopeProcessor,
                                           state: ResolveState,
                                           lastParent: PsiElement,
                                           place: PsiElement): Boolean = {
    implicit val context: ProjectContext = place

    processSyntheticClass(api.AnyRef, processor, state, lastParent, place) &&
      processSyntheticClass(api.Any, processor, state, lastParent, place)
  }

  private def processSyntheticClass(stdType: StdType,
                                    processor: PsiScopeProcessor,
                                    state: ResolveState,
                                    lastParent: PsiElement,
                                    place: PsiElement): Boolean = {
    stdType.syntheticClass.forall(_.processDeclarations(processor, state, lastParent, place))
  }

}

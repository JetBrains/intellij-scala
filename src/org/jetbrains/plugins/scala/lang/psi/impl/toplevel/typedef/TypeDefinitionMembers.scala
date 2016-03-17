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
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.convertMemberName
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAccessModifier, ScFieldId, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedPrefixReference
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.resolve.processor._
import org.jetbrains.plugins.scala.macroAnnotations.CachedInsidePsiElement
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.reflect.NameTransformer

/**
 * @author ven
 * @author alefas
 */
object TypeDefinitionMembers {
  def nonBridge(place: Option[PsiElement], memb: PsiMember): Boolean = {
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

    def computeHashCode(s: Signature) = s.simpleHashCode

    def elemName(t: Signature) = t.name


    def same(t1: Signature, t2: Signature): Boolean = {
      t1.namedElement eq t2.namedElement
    }

    def isPrivate(t: Signature): Boolean = {
      t.namedElement match {
        case param: ScClassParameter if !param.isEffectiveVal => true
        case named: ScNamedElement =>
          ScalaPsiUtil.nameContext(named) match {
            case s: ScModifierListOwner =>
              s.getModifierList.accessModifier match {
                case Some(a: ScAccessModifier) => a.isUnqualifiedPrivateOrThis
                case _ => false
              }
            case _ => false
          }
        case n: PsiModifierListOwner =>
          n.hasModifierProperty("private")
        case _ => false
      }
    }

    def isAbstract(s: Signature) = s match {
      case phys: PhysicalSignature => TypeDefinitionMembers.this.isAbstract(phys)
      case s: Signature => s.namedElement match {
        case _: ScFieldId => true
        case f: PsiField if f.hasModifierProperty(PsiModifier.ABSTRACT) => true
        case _ => false
      }
      case _ => false
    }

    def isImplicit(t: Signature) = ScalaPsiUtil.isImplicit(t.namedElement)

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement])
                   (implicit typeSystem: TypeSystem) {
      for (method <- clazz.getMethods if nonBridge(place, method) &&
        !method.isConstructor && !method.hasModifierProperty("static") &&
        method.getParameterList.getParametersCount == 0) {
        val phys = new PhysicalSignature(method, subst)
        map addToMap (phys, new Node(phys, subst))
      }

      for (field <- clazz.getFields if nonBridge(place, field) && !field.hasModifierProperty("static")) {
        val sig = new Signature(field.getName, Seq.empty, 0, subst, field)
        map addToMap (sig, new Node(sig, subst))
      }
    }

    def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map,
                     place: Option[PsiElement], base: Boolean)
                    (implicit typeSystem: TypeSystem) {
      def addSignature(s: Signature) {
        map addToMap (s, new Node(s, subst))
      }

      if (template.qualifiedName == "scala.AnyVal") {
        //we need to add Object members
        val obj = ScalaPsiManager.instance(template.getProject).getCachedClass(template.getResolveScope, "java.lang.Object")
        obj.map { obj =>
          for (method <- obj.getMethods) {
            method.getName match {
              case "hashCode" | "toString" =>
                addSignature(new PhysicalSignature(method, ScSubstitutor.empty))
              case _ =>
            }
          }
        }
      }

      for (member <- template.members) {
        member match {
          case _var: ScVariable if nonBridge(place, _var) =>
            for (dcl <- _var.declaredElements) {
              addSignature(new Signature(dcl.name, Seq.empty, 0, subst, dcl))
              dcl.nameContext match {
                case s: ScAnnotationsHolder =>
                  val beanProperty = ScalaPsiUtil.isBeanProperty(s, noResolve = true)
                  val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(s, noResolve = true)
                  if (beanProperty) {
                    addSignature(new Signature("get" + dcl.name.capitalize, Seq.empty, 0, subst, dcl))
                  } else if (booleanBeanProperty) {
                    addSignature(new Signature("is" + dcl.name.capitalize, Seq.empty, 0, subst, dcl))
                  }
                case _ =>
              }
            }
          case _val: ScValue if nonBridge(place, _val) =>
            for (dcl <- _val.declaredElements) {
              addSignature(new Signature(dcl.name, Seq.empty, 0, subst, dcl))
              dcl.nameContext match {
                case s: ScAnnotationsHolder =>
                  val beanProperty = ScalaPsiUtil.isBeanProperty(s, noResolve = true)
                  val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(s, noResolve = true)
                  if (beanProperty) {
                    addSignature(new Signature("get" + dcl.name.capitalize, Seq.empty, 0, subst, dcl))
                  } else if (booleanBeanProperty) {
                    addSignature(new Signature("is" + dcl.name.capitalize, Seq.empty, 0, subst, dcl))
                  }
                case _ =>
              }
            }
          case constr: ScPrimaryConstructor =>
            val parameters = constr.parameters
            for (param <- parameters if nonBridge(place, param)) {
               addSignature(new Signature(param.name, Seq.empty, 0, subst, param))
              val beanProperty = ScalaPsiUtil.isBeanProperty(param, noResolve = true)
              val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(param, noResolve = true)
              if (beanProperty) {
                addSignature(new Signature("get" + param.name.capitalize, Seq.empty, 0, subst, param))
              } else if (booleanBeanProperty) {
                addSignature(new Signature("is" + param.name.capitalize, Seq.empty, 0, subst, param))
              }
            }
          case f: ScFunction if nonBridge(place, f) && !f.isConstructor && f.parameters.isEmpty =>
            addSignature(new PhysicalSignature(f, subst))
          case o: ScObject if nonBridge(place, o) =>
            addSignature(new Signature(o.name, Seq.empty, 0, subst, o))
          case c: ScTypeDefinition if c.fakeCompanionModule.isDefined && nonBridge(place, c) =>
            val o = c.fakeCompanionModule.get
            addSignature(new Signature(o.name, Seq.empty, 0, subst, o))
          case _ =>
        }
      }

      for (method <- template.syntheticMethodsWithOverride if method.getParameterList.getParametersCount == 0) {
        val sig = new PhysicalSignature(method, subst)
        addSignature(sig)
      }

      for (td <- template.syntheticTypeDefinitions) {
        td match {
          case obj: ScObject => addSignature(new Signature(obj.name, Seq.empty, 0, subst, obj))
          case td: ScTypeDefinition =>
            td.fakeCompanionModule match {
              case Some(obj) => addSignature(new Signature(obj.name, Seq.empty, 0, subst, obj))
              case _ =>
            }
          case _ =>
        }
      }

      if (!base) {
        for (method <- template.syntheticMethodsNoOverride if method.getParameterList.getParametersCount == 0) {
          val sig = new PhysicalSignature(method, subst)
          addSignature(sig)
        }
      }
    }

    def processRefinement(cp: ScCompoundType, map: Map, place: Option[PsiElement])
                         (implicit typeSystem: TypeSystem) {
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

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement])
                   (implicit typeSystem: TypeSystem) {
      for (inner <- clazz.getInnerClasses if nonBridge(place, inner) &&
        !inner.hasModifierProperty("static")) {
        map addToMap (inner, new Node(inner, subst))
      }
    }

    def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map,
                     place: Option[PsiElement], base: Boolean)
                    (implicit typeSystem: TypeSystem) {
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
                         (implicit typeSystem: TypeSystem) {
      for ((name, TypeAliasSignature(_, _, _, _, _, alias)) <- cp.typesMap if nonBridge(place, alias)) {
        map addToMap (alias, new Node(alias, ScSubstitutor.empty))
      }
    }
  }

  object SignatureNodes extends MixinNodes {
    type T = Signature

    def equiv(s1: Signature, s2: Signature) = s1 equiv s2

    def computeHashCode(s: Signature) = s.simpleHashCode

    def elemName(t: Signature) = t.name

    def same(t1: Signature, t2: Signature): Boolean = {
      t1.namedElement eq t2.namedElement
    }

    def isPrivate(t: Signature): Boolean = {
      t.namedElement match {
        case c: ScClassParameter if !c.isEffectiveVal => true
        case named: ScNamedElement =>
          ScalaPsiUtil.nameContext(named) match {
            case s: ScModifierListOwner =>
              s.getModifierList.accessModifier match {
                case Some(a: ScAccessModifier) => a.isUnqualifiedPrivateOrThis
                case _ => false
              }
            case _ => false
          }
        case n: PsiModifierListOwner =>
          n.hasModifierProperty("private")
        case _ => false
      }
    }

    def isAbstract(s: Signature) = s match {
      case phys: PhysicalSignature => TypeDefinitionMembers.this.isAbstract(phys)
      case s: Signature => s.namedElement match {
        case _: ScFieldId => true
        case f: PsiField if f.hasModifierProperty(PsiModifier.ABSTRACT) => true
        case _ => false
      }
      case _ => false
    }

    def isImplicit(t: Signature) = ScalaPsiUtil.isImplicit(t.namedElement)

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement])
                   (implicit typeSystem: TypeSystem) {
      for (method <- clazz.getMethods if nonBridge(place, method) &&
        !method.isConstructor && !method.hasModifierProperty("static")) {
        val phys = new PhysicalSignature(method, subst)
        map addToMap (phys, new Node(phys, subst))
      }

      for (field <- clazz.getFields if nonBridge(place, field) && !field.hasModifierProperty("static")) {
        val sig = new Signature(field.getName, Seq.empty, 0, subst, field)
        map addToMap (sig, new Node(sig, subst))
      }
    }

    def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map,
                     place: Option[PsiElement], base: Boolean)
                    (implicit typeSystem: TypeSystem) {
      def addSignature(s: Signature) {
        map addToMap (s, new Node(s, subst))
      }

      if (template.qualifiedName == "scala.AnyVal") {
        //we need to add Object members
        val obj = ScalaPsiManager.instance(template.getProject).getCachedClass(template.getResolveScope, "java.lang.Object")
        obj.map { obj =>
          for (method <- obj.getMethods) {
            method.getName match {
              case "equals" | "hashCode" | "toString" =>
                addSignature(new PhysicalSignature(method, ScSubstitutor.empty))
              case _ =>
            }
          }
        }
      }

      for (member <- template.members) {
        member match {
          case _var: ScVariable if nonBridge(place, _var) =>
            for (dcl <- _var.declaredElements) {
              lazy val t = dcl.getType(TypingContext.empty).getOrAny
              addSignature(new Signature(dcl.name, Seq.empty, 0, subst, dcl))
              addSignature(new Signature(dcl.name + "_=", Seq(() => t), 1, subst, dcl))
              dcl.nameContext match {
                case s: ScAnnotationsHolder =>
                  val beanProperty = ScalaPsiUtil.isBeanProperty(s, noResolve = true)
                  val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(s, noResolve = true)
                  if (beanProperty) {
                    addSignature(new Signature("get" + dcl.name.capitalize, Seq.empty, 0, subst, dcl))
                  } else if (booleanBeanProperty) {
                    addSignature(new Signature("is" + dcl.name.capitalize, Seq.empty, 0, subst, dcl))
                  }
                  if (beanProperty || booleanBeanProperty) {
                    addSignature(new Signature("set" + dcl.name.capitalize, Seq(() => t), 1,
                      subst, dcl))
                  }
                case _ =>
              }
            }
          case _val: ScValue if nonBridge(place, _val) =>
            for (dcl <- _val.declaredElements) {
              addSignature(new Signature(dcl.name, Seq.empty, 0, subst, dcl))
              dcl.nameContext match {
                case s: ScAnnotationsHolder =>
                  val beanProperty = ScalaPsiUtil.isBeanProperty(s, noResolve = true)
                  val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(s, noResolve = true)
                  if (beanProperty) {
                    addSignature(new Signature("get" + dcl.name.capitalize, Seq.empty, 0, subst, dcl))
                  } else if (booleanBeanProperty) {
                    addSignature(new Signature("is" + dcl.name.capitalize, Seq.empty, 0, subst, dcl))
                  }
                case _ =>
              }
            }
          case constr: ScPrimaryConstructor =>
            val parameters = constr.parameters
            for (param <- parameters if nonBridge(place, param)) {
              lazy val t = param.getType(TypingContext.empty).getOrAny
              addSignature(new Signature(param.name, Seq.empty, 0, subst, param))
              if (!param.isStable) addSignature(new Signature(param.name + "_=", Seq(() => t), 1, subst,
                param))
              val beanProperty = ScalaPsiUtil.isBeanProperty(param, noResolve = true)
              val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(param, noResolve = true)
              if (beanProperty) {
                addSignature(new Signature("get" + param.name.capitalize, Seq.empty, 0, subst, param))
                if (!param.isStable) {
                  addSignature(new Signature("set" + param.name.capitalize, Seq(() => t), 1,
                    subst, param))
                }
              } else if (booleanBeanProperty) {
                addSignature(new Signature("is" + param.name.capitalize, Seq.empty, 0, subst, param))
                if (!param.isStable) {
                  addSignature(new Signature("set" + param.name.capitalize, Seq(() => t), 1,
                    subst, param))
                }
              }
            }
          case f: ScFunction if nonBridge(place, f) && !f.isConstructor =>
            addSignature(new PhysicalSignature(f, subst))
          case o: ScObject if nonBridge(place, o) =>
            addSignature(new Signature(o.name, Seq.empty, 0, subst, o))
          case c: ScTypeDefinition =>
            if (c.fakeCompanionModule.isDefined && nonBridge(place, c)) {
              val o = c.fakeCompanionModule.get
              addSignature(new Signature(o.name, Seq.empty, 0, subst, o))
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

      for (method <- template.syntheticMethodsWithOverride) {
        val sig = new PhysicalSignature(method, subst)
        addSignature(sig)
      }

      for (td <- template.syntheticTypeDefinitions) {
        td match {
          case obj: ScObject => addSignature(new Signature(obj.name, Seq.empty, 0, subst, obj))
          case td: ScTypeDefinition =>
            td.fakeCompanionModule match {
              case Some(obj) => addSignature(new Signature(obj.name, Seq.empty, 0, subst, obj))
              case _ =>
            }
          case _ =>
        }
      }

      if (!base) {
        for (member <- template.syntheticMethodsNoOverride) {
          val sig = new PhysicalSignature(member, subst)
          addSignature(sig)
        }
      }
    }

    def processRefinement(cp: ScCompoundType, map: Map, place: Option[PsiElement])
                         (implicit typeSystem: TypeSystem) {
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
      for {
        signature <- TypeDefinitionMembers.getSignatures(c).allFirstSeq()
        (_, node) <- signature
      } action(node)
    }
  }

  import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.ParameterlessNodes.{Map => PMap}
  import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.SignatureNodes.{Map => SMap}
  import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.TypeNodes.{Map => TMap}

  def getParameterlessSignatures(clazz: PsiClass): PMap = {
    implicit val typeSystem = clazz.getProject.typeSystem
    @CachedInsidePsiElement(clazz, CachesUtil.getDependentItem(clazz)())
    def inner(): PMap = ParameterlessNodes.build(clazz)

    clazz match {
      case o: ScObject =>
        val qual = o.qualifiedName
        if (qual == "scala" || qual == "scala.Predef") {
          return o.getHardParameterlessSignatures
        }
      case _ =>
    }

    inner()
  }

  def getTypes(clazz: PsiClass): TMap = {
    implicit val typeSystem = clazz.getProject.typeSystem
    @CachedInsidePsiElement(clazz, CachesUtil.getDependentItem(clazz)())
    def inner(): TMap =TypeNodes.build(clazz)

    clazz match {
      case o: ScObject =>
        val qual = o.qualifiedName
        if (qual == "scala" || qual == "scala.Predef") {
          return o.getHardTypes
        }
      case _ =>
    }

    inner()
  }

  def getSignatures(clazz: PsiClass, place: Option[PsiElement] = None): SMap = {
    implicit val typeSystem = clazz.getProject.typeSystem
    @CachedInsidePsiElement(clazz, CachesUtil.getDependentItem(clazz)())
    def buildNodesClass(): SMap = SignatureNodes.build(clazz)

    clazz match {
      case o: ScObject =>
        val qual = o.qualifiedName
        if (qual == "scala" || qual == "scala.Predef") {
          return o.getHardSignatures
        }
      case _ =>
    }
    val ans = buildNodesClass()
    place.foreach {
      case _: ScInterpolatedPrefixReference =>
        val allowedNames = ans.keySet
        for (child <- clazz.getChildren) {
          child match {
            case n: ScExtendsBlock =>
              val children = n.getFirstChild.getChildren
              for (c <- children) {
                c match {
                  case o: ScObject =>
                    if (allowedNames.contains(o.name)) {
                      @CachedInsidePsiElement(o, CachesUtil.getDependentItem(o)())
                      def buildNodesObject(): SMap = SignatureNodes.build(o)

                      val add = buildNodesObject()
                      ans ++= add
                    }
                  case c: ScClass =>
                    if (allowedNames.contains(c.name)) {
                      @CachedInsidePsiElement(c, CachesUtil.getDependentItem(c)())
                      def buildNodesClass2(): SMap = SignatureNodes.build(c)

                      val add = buildNodesClass2()
                      ans ++= add
                    }
                  case _ =>
                }
              }
            case _ =>
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
            val clazzType = td.getTypeWithProjections(TypingContext.empty).getOrAny
            Bounds.glb(selfType, clazzType) match {
              case c: ScCompoundType =>
                getSignatures(c, Some(clazzType), clazz)
              case tp =>
                val cl = ScType.extractClassType(tp, Some(clazz.getProject)) match {
                  case Some((selfClazz, subst)) => selfClazz
                  case _ => clazz
                }
                getSignatures(cl)
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
              case tp =>
                val cl = ScType.extractClassType(tp, Some(clazz.getProject)) match {
                  case Some((selfClazz, subst)) => selfClazz
                  case _ => clazz
                }
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
                          place: PsiElement)
                         (implicit typeSystem: TypeSystem): Boolean = {
    def signaturesForJava: SignatureNodes.Map = {
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

    def syntheticMethods: Seq[(Signature, SignatureNodes.Node)] = {
      clazz match {
        case td: ScTemplateDefinition => td.syntheticMethodsNoOverride.map(fun => {
          val f = new PhysicalSignature(fun, ScSubstitutor.empty)
          (f, new SignatureNodes.Node(f, ScSubstitutor.empty))
        })
        case _ => Seq.empty
      }
    }

    if (BaseProcessor.isImplicitProcessor(processor) && !clazz.isInstanceOf[ScTemplateDefinition]) return true

    if (!privateProcessDeclarations(processor, state, lastParent, place, () => getSignatures(clazz, Option(place)),
      () => getParameterlessSignatures(clazz), () => getTypes(clazz), isSupers = false,
      isObject = clazz.isInstanceOf[ScObject], signaturesForJava = () => signaturesForJava,
      syntheticMethods = () => syntheticMethods)) return false

    if (!(types.AnyRef.asClass(clazz.getProject).getOrElse(return true).processDeclarations(processor, state, lastParent, place) &&
            types.Any.asClass(clazz.getProject).getOrElse(return true).processDeclarations(processor, state, lastParent, place))) return false

    if (shouldProcessMethods(processor) && !processEnum(clazz, processor.execute(_, state))) return false
    true
  }

  def processSuperDeclarations(td: ScTemplateDefinition,
                               processor: PsiScopeProcessor,
                               state: ResolveState,
                               lastParent: PsiElement,
                               place: PsiElement): Boolean = {
    if (!privateProcessDeclarations(processor, state, lastParent, place, () => getSignatures(td),
      () => getParameterlessSignatures(td), () => getTypes(td), isSupers = true, isObject = td.isInstanceOf[ScObject])) return false

    if (!(types.AnyRef.asClass(td.getProject).getOrElse(return true).
      processDeclarations(processor, state, lastParent, place) &&
            types.Any.asClass(td.getProject).getOrElse(return true).
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
      () => getSignatures(comp, compoundTypeThisType, place), () => getParameterlessSignatures(comp, compoundTypeThisType, place),
      () => getTypes(comp, compoundTypeThisType, place), isSupers = false, isObject = false)) return false

    val project =
      if (lastParent != null) lastParent.getProject
      else if (place != null) place.getProject
      else return true
    if (!(types.AnyRef.asClass(project).getOrElse(return true).processDeclarations(processor, state, lastParent, place) &&
            types.Any.asClass(project).getOrElse(return true).processDeclarations(processor, state, lastParent, place)))
      return false

    true
  }

  class Lazy[T](private var thunk: () => T) {
    private var value: T = _
    def apply() = {
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
                                         isObject: Boolean,
                                         signaturesForJava: Lazy[SignatureNodes.Map] = () => new SignatureNodes.Map,
                                         syntheticMethods: Lazy[Seq[(Signature, SignatureNodes.Node)]] = () => Seq.empty
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
    val processMethodRefs = shouldProcessMethodRefs(processor)
    val processValsForScala = isScalaProcessor && processVals
    val processOnlyStable = shouldProcessOnlyStable(processor)

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
                  if (!processor.execute(elem, state.put(ScSubstitutor.key, n.substitutor followed subst)))
                    return false
                } else {
                  if (n.supers.nonEmpty &&
                    !processor.execute(n.supers.apply(0).info.asInstanceOf[Signature].namedElement,
                      state.put(ScSubstitutor.key, n.supers.apply(0).substitutor followed subst))) return false
                }
              } else if (!tail) return false
            case _ => if (!tail) return false
          }
          def tail: Boolean = {
            if (processValsForScala && checkName(elem.name) &&
              !processor.execute(elem, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false

            if (name == null || name.isEmpty || checkName(s"${elem.name}_=")) {
              elem match {
                case t: ScTypedDefinition if t.isVar && signature.name.endsWith("_=") =>
                  if (processValsForScala && !processor.execute(t.getUnderEqualsMethod,
                    state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
                case _ =>
              }
            }

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

        def addSignature(sig: T#T, n: T#Node): Boolean = {
          import scala.language.existentials

          ProgressManager.checkCanceled()
          def addMethod(method: PsiNamedElement): Boolean = {
            if (checkName(method.name)) {
              val substitutor = n.substitutor followed subst
              if (!processor.execute(method, state.put(ScSubstitutor.key, substitutor))) return false
            }
            true
          }
          sig match {
            case phys: PhysicalSignature if processMethods => if (!addMethod(phys.method)) return false
            case phys: PhysicalSignature => //do nothing
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
                def addMethod(method: PsiNamedElement): Boolean = {
                  val substitutor = n.substitutor followed subst
                  processor.execute(method, state.put(ScSubstitutor.key, substitutor))
                }

                n.info match {
                  case phys: PhysicalSignature if processMethods => if (!addMethod(phys.method)) return false
                  case phys: PhysicalSignature => //do nothing
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
          val implicits: List[(T#T, T#Node)] = signatures.forImplicits()
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
          val maps = signaturesForJava().allFirstSeq()
          val valuesIterator = maps.iterator
          while (valuesIterator.hasNext) {
            val iterator = valuesIterator.next().iterator
            runIterator(iterator) match {
              case Some(x) => return x
              case None =>
            }
          }

          //todo: this is hack, better to split imports resolve into import for types and for expressions.
          runIterator(syntheticMethods().iterator) match {
            case Some(x) => return x
            case None =>
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
          if (!processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
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
              if (!processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
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
          if (n.info.isInstanceOf[ScTypeDefinition] &&
            !processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
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
    case _ =>
      val hint: ElementClassHint = processor.getHint(ElementClassHint.KEY)
      hint == null || hint.shouldProcess(ElementClassHint.DeclarationKind.VARIABLE)
  }

  def shouldProcessMethods(processor: PsiScopeProcessor) = processor match {
    case BaseProcessor(kinds) => kinds contains METHOD
    case _ =>
      val hint = processor.getHint(ElementClassHint.KEY)
      hint == null || hint.shouldProcess(ElementClassHint.DeclarationKind.METHOD)
  }

  def shouldProcessMethodRefs(processor: PsiScopeProcessor) = processor match {
    case BaseProcessor(kinds) => (kinds contains METHOD) || (kinds contains VAR) || (kinds contains VAL)
    case _ => true
  }

  def shouldProcessTypes(processor: PsiScopeProcessor) = processor match {
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
        case method =>
          method.getName == "values" && method.getParameterList.getParametersCount == 0 &&
            method.hasModifierProperty("static")
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
}

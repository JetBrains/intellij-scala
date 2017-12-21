package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi._
import com.intellij.psi.impl.source.resolve.JavaResolveUtil
import com.intellij.psi.scope.{NameHint, PsiScopeProcessor}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSelfTypeElement, ScTypeElement, ScTypeVariableTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAccessModifier, ScFieldId, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScSuperReference, ScThisReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.{ScSyntheticClass, ScSyntheticValue}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.impl.{ScPackageImpl, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.light.scala.isLightScNamedElement
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScThisType
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue._
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, ResolveProcessor}
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import _root_.scala.collection.Set

/**
 * @author ven
 */
object ResolveUtils {
  def kindMatches(element: PsiElement, kinds: Set[ResolveTargets.Value]): Boolean = kinds == null ||
          (element match {
            case _: PsiPackage | _: ScPackaging => kinds contains PACKAGE
            case obj: ScObject if obj.isPackageObject => kinds contains PACKAGE
            case _: ScObject => (kinds contains OBJECT) || (kinds contains METHOD)
            case _: ScTypeVariableTypeElement => kinds contains CLASS
            case _: ScTypeParam => kinds contains CLASS
            case _: ScTypeAlias => kinds contains CLASS
            case _: ScTypeDefinition => kinds contains CLASS
            case _: ScSyntheticClass => kinds contains CLASS
            case c: PsiClass =>
              if (kinds contains CLASS) true
              else {
                def isStaticCorrect(clazz: PsiClass): Boolean = {
                  val cclazz = clazz.getContainingClass
                  cclazz == null || (clazz.hasModifierProperty(PsiModifier.STATIC) && isStaticCorrect(cclazz))
                }
                (kinds contains OBJECT) && isStaticCorrect(c)
              }
            case patt: ScBindingPattern =>
              val value = patt.nonStrictParentOfType(Seq(classOf[ScVariable], classOf[ScValue])) match {
                case Some(_: ScVariable) => VAR
                case _ => VAL
              }
              kinds.contains(value)
            case patt: ScFieldId =>
              if (patt.getParent /*list of ids*/ .getParent.isInstanceOf[ScVariable])
                kinds contains VAR else kinds contains VAL
            case classParam: ScClassParameter =>
              if (classParam.isVar) kinds.contains(VAR) else kinds.contains(VAL)
            case _: ScParameter => kinds contains VAL
            case _: ScSelfTypeElement => kinds contains VAL
            case _: PsiMethod => kinds contains METHOD
            case _: ScFun => kinds contains METHOD
            case _: ScSyntheticValue => kinds contains VAL
            case f: PsiField => (kinds contains VAR) || (f.hasModifierPropertyScala(PsiModifier.FINAL) && kinds.contains(VAL))
            case _: PsiParameter => kinds contains VAL //to enable named Parameters resolve in Play 2.0 routing file for java methods
            case _ => false
          })

  def javaMethodType(m: PsiMethod, s: ScSubstitutor, scope: GlobalSearchScope, returnType: Option[ScType] = None): ScMethodType = {
    implicit val elementScope = ElementScope(m.getProject, scope)

    val retType: ScType = (m, returnType) match {
      case (f: FakePsiMethod, None) => s.subst(f.retType)
      case (_, None) => s.subst(m.getReturnType.toScType())
      case (_, Some(x)) => x
    }

    ScMethodType(retType,
      m match {
        case f: FakePsiMethod => f.params.toSeq
        case _ =>
          m.parameters.map { param =>
            val scType = s.subst(param.paramType())
            Parameter("", None, scType, scType, isDefault = false, isRepeated = param.isVarArgs, isByName = false, param.index, Some(param))
          }
      }, isImplicit = false)
  }

  def javaPolymorphicType(m: PsiMethod, s: ScSubstitutor, scope: GlobalSearchScope = null, returnType: Option[ScType] = None): NonValueType = {
    if (m.getTypeParameters.isEmpty) javaMethodType(m, s, scope, returnType)
    else {
      ScTypePolymorphicType(javaMethodType(m, s, scope, returnType), m.getTypeParameters.map(TypeParameter(_)))
    }
  }

  def isAccessible(memb: PsiMember, _place: PsiElement, forCompletion: Boolean = false): Boolean = {
    var place = _place
    memb match {
      case b: ScBindingPattern =>
        b.nameContext match {
          case memb: ScMember => return isAccessible(memb, place)
          case _ => return true
        }
      //todo: ugly workaround, probably FakePsiMethod is better to remove?
      case FakePsiMethod(method: PsiMember) => return isAccessible(method, place)
      case _: FakePsiMethod =>
      case isLightScNamedElement(named: ScMember) => return isAccessible(named, place)
      case _ =>
    }
    if (place.getLanguage == JavaLanguage.INSTANCE) {
      return JavaResolveUtil.isAccessible(memb, memb.containingClass, memb.getModifierList, place, null, null)
    }

    import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getPlaceTd
    //this is to make place and member on same level (resolve from library source)
    var member: PsiMember = memb
    memb.getContainingFile match {
      case file: ScalaFile if file.isCompiled =>
        place.getContainingFile match {
          case file: ScalaFile if file.isCompiled =>
          case _ if !member.isInstanceOf[ScMember] =>
            member = member.getOriginalElement.asInstanceOf[PsiMember]
          case _ => //todo: is it neccessary? added to avoid performance and other problems
        }
      case _ =>
    }
    if (forCompletion && place != null) {
      val originalFile: PsiFile = place.getContainingFile.getOriginalFile
      if (originalFile == member.getContainingFile) {
        val newPlace = originalFile.findElementAt(place.getTextRange.getStartOffset)
        place = newPlace
      }
    }

    member match {
      case f: ScFunction if f.isBridge => return false
      case _ =>
    }

    def checkProtected(td: PsiClass, withCompanion: Boolean): Boolean = {
      val isConstr = member match {
        case m: PsiMethod => m.isConstructor
        case _ => false
      }
      var placeTd: ScTemplateDefinition = getPlaceTd(place, isConstr)
      if (isConstr) {
        if (placeTd != null && !placeTd.isInstanceOf[ScTypeDefinition] && placeTd.extendsBlock.templateBody.isEmpty) {
          placeTd = getPlaceTd(placeTd)
        } else if (placeTd != null) {
          if (td != null && isInheritorOrSelfOrSame(placeTd, td)) return true
        }
        while (placeTd != null) {
          if (td == placeTd) return true
          val companion = getCompanionModule(placeTd).orNull
          if (companion != null && companion == td) return true
          placeTd = getPlaceTd(placeTd)
        }
        return false
      }
      while (placeTd != null) {
        if (td != null && isInheritorOrSelfOrSame(placeTd, td)) return true
        val companion = getCompanionModule(placeTd).orNull
        if (withCompanion && companion != null && td != null && isInheritorDeep(companion, td)) return true
        placeTd = getPlaceTd(placeTd)
      }
      false
    }

    member match {
      case scMember: ScMember => scMember.getModifierList.accessModifier match {
        case None => true
        case Some(am: ScAccessModifier) =>
          if (am.isPrivate) {
            if (am.access == ScAccessModifier.Type.THIS_PRIVATE) {
              val containingClass = scMember.containingClass
              if (containingClass == null) return true

              if (scMember.hasModifierProperty("implicit"))
                return PsiTreeUtil.isContextAncestor(containingClass, place, false)
              /*
              ScalaRefernce.pdf:
                A member M marked with this modifier can be accessed only from
                within the object in which it is defined.
              */
              place match {
                case ref: ScReferenceElement =>
                  ref.qualifier match {
                    case None =>
                      return PsiTreeUtil.isContextAncestor(containingClass, place, false)
                    case Some(t: ScThisReference) =>
                      return t.refTemplate match {
                        case Some(templ) => templ == containingClass
                        case _ => PsiTreeUtil.isContextAncestor(containingClass, place, false)
                      }
                    case Some(ref: ScReferenceElement) =>
                      val resolve = ref.resolve()
                      if (containingClass.extendsBlock.selfTypeElement.contains(resolve)) return true
                      else return false
                    case _ => return false
                  }
                case _ =>
                  return PsiTreeUtil.isContextAncestor(containingClass, place, false)
              }
            }
            val ref = am.getReference
            if (ref != null) {
              val bind = ref.resolve
              if (bind == null) return true
              def processPackage(packageName: String): Boolean = {
                def context(place: PsiElement): PsiElement =
                  getContextOfType(place, true, classOf[ScPackaging],
                    classOf[ScObject], classOf[ScalaFile])
                var placeEnclosing: PsiElement = context(place)
                while (placeEnclosing != null && placeEnclosing.isInstanceOf[ScObject] &&
                         !placeEnclosing.asInstanceOf[ScObject].isPackageObject)
                  placeEnclosing = context(placeEnclosing)
                if (placeEnclosing == null) return false //not Scala
                val placePackageName = placeEnclosing match {
                  case _: ScalaFile => ""
                  case obj: ScObject => obj.qualifiedName
                  case pack: ScPackaging => pack.fullPackageName
                }
                packageContains(packageName, placePackageName)
              }
              bind match {
                case td: ScTemplateDefinition if smartContextAncestor(td, place, checkCompanion = true) =>
                  true
                case obj: ScObject =>
                  obj.isPackageObject && processPackage(obj.qualifiedName)
                case pack: PsiPackage =>
                  val packageName = pack.getQualifiedName
                  processPackage(packageName)
                case _ => true
              }
            }
            else {
              /*
              ScalaReference.pdf:
                Such members can be accessed only from within the directly enclosing
                template and its companion module or companion class
              */
              val enclosing = getContextOfType(scMember, true,
                classOf[ScalaFile], classOf[ScPackaging], classOf[ScTemplateDefinition])
              enclosing match {
                case td: ScTemplateDefinition =>
                  smartContextAncestor(td, place, checkCompanion = true)
                case file: ScalaFile if file.isScriptFile =>
                  PsiTreeUtil.isContextAncestor(file, place, false)
                case _ =>
                  getContextOfType(place, true, classOf[ScPackaging], classOf[ScalaFile]) match {
                    case null => false // not Scala
                    case placeEnclosing =>
                      def packaging(element: PsiElement) = Option(element).collect {
                        case packaging: ScPackaging => packaging
                      }.map {
                        _.fullPackageName
                      }.getOrElse("")

                      packageContains(packaging(enclosing), packaging(placeEnclosing))
                  }
              }
            }
          } else if (am.isProtected) { //todo: it's wrong if reference after not appropriate class type
            val withCompanion = am.access != ScAccessModifier.Type.THIS_PROTECTED
            val ref = am.getReference
            if (ref != null) {
              val bind = ref.resolve
              if (bind == null) return true
              def processPackage(packageName: String): Option[Boolean] = {
                def context(place: PsiElement): PsiElement =
                  getContextOfType(place, true, classOf[ScPackaging],
                    classOf[ScObject], classOf[ScalaFile])
                var placeEnclosing: PsiElement = context(place)
                while (placeEnclosing != null && placeEnclosing.isInstanceOf[ScObject] &&
                         !placeEnclosing.asInstanceOf[ScObject].isPackageObject)
                  placeEnclosing = context(placeEnclosing)
                if (placeEnclosing == null) return Some(false) //not Scala
                val placePackageName = placeEnclosing match {
                  case _: ScalaFile => ""
                  case obj: ScObject => obj.qualifiedName
                  case pack: ScPackaging => pack.fullPackageName
                }
                if (packageContains(packageName, placePackageName)) return Some(true)
                None
              }
              bind match {
                case td: ScTemplateDefinition =>
                  if (smartContextAncestor(td, place, checkCompanion = true)) return true
                  td match {
                    case o: ScObject if o.isPackageObject =>
                      processPackage(o.qualifiedName) match {
                        case Some(x) => return x
                        case None =>
                      }
                    case _ =>
                  }
                case pack: PsiPackage => //like private (nothing related to real life)
                  val packageName = pack.getQualifiedName
                  processPackage(packageName) match {
                    case Some(x) => return x
                    case None =>
                  }
                case _ => return true
              }
            }
            val enclosing = getContextOfType(scMember, true,
              classOf[ScalaFile], classOf[ScTemplateDefinition], classOf[ScPackaging])
            assert(enclosing != null, s"Enclosing is null in file ${scMember.getContainingFile.getName}:\n${scMember.getContainingFile.getText}")
            if (am.isThis) {
              place match {
                case ref: ScReferenceElement =>
                  ref.qualifier match {
                    case None =>
                    case Some(_: ScThisReference) =>
                    case Some(_: ScSuperReference) =>
                    case Some(ResolvesTo(_: ScSelfTypeElement)) =>
                      val enclosing = PsiTreeUtil.getContextOfType(scMember, true, classOf[ScTemplateDefinition])
                      if (enclosing == null) return false
                    case _ => return false
                  }
                case _ =>
              }
            }
            enclosing match {
              case td: ScTypeDefinition =>
                if (smartContextAncestor(td, place, withCompanion)) return true
                checkProtected(td, withCompanion)
              case td: ScTemplateDefinition =>
                //it'd anonymous class, has access only inside
                PsiTreeUtil.isContextAncestor(td, place, false)
              case _ =>
                //same as for private
                val packageName = enclosing match {
                  case _: ScalaFile => ""
                  case packaging: ScPackaging => packaging.fullPackageName
                }
                val placeEnclosing: PsiElement = getContextOfType(place, true, classOf[ScPackaging], classOf[ScalaFile])
                if (placeEnclosing == null) return false //not Scala
                val placePackageName = placeEnclosing match {
                  case _: ScalaFile => ""
                  case pack: ScPackaging => pack.fullPackageName
                }
                packageContains(packageName, placePackageName)
            }
          } else true
      }
      case _ =>
        if (member.hasModifierProperty("public")) true
        else if (member.hasModifierProperty("private")) false
        else if (member.hasModifierProperty("protected") &&
                checkProtected(member.containingClass, withCompanion = true)) true
        else {
          val packageName = member.getContainingFile match {
            case _: ScalaFile => ""
            case f: PsiClassOwner => f.getPackageName
            case _ => return false
          }
          val placeEnclosing: PsiElement = getContextOfType(place, true, classOf[ScPackaging], classOf[ScalaFile])
          if (placeEnclosing == null) return false
          val placePackageName = placeEnclosing match {
            case _: ScalaFile => ""
            case pack: ScPackaging => pack.fullPackageName
          }
          packageContains(packageName, placePackageName)
        }
    }
  }

  def processSuperReference(superRef: ScSuperReference, processor : BaseProcessor, place : ScalaPsiElement) {
    if (superRef.isHardCoded) {
      superRef.drvTemplate match {
        case Some(c) => processor.processType(ScThisType(c), place)
        case None =>
      }
    } else {
      superRef.staticSuper match {
        case Some(t) => processor.processType(t, place)
        case None => superRef.drvTemplate match {
          case Some(c) =>
            TypeDefinitionMembers.processSuperDeclarations(c, processor, ResolveState.initial.put(ScSubstitutor.key, ScSubstitutor.empty), null, place)
          case None =>
        }
      }
    }
  }

  def getPlacePackage(place: PsiElement): String = {
    val pack: ScPackaging = getContextOfType(place, true, classOf[ScPackaging]) match {
      case pack: ScPackaging => pack
      case _ => null
    }
    if (pack == null) return ""
    pack.fullPackageName
  }

  private def sameOrInheritor(cl1: PsiClass, cl2: PsiClass): Boolean =
    ScEquivalenceUtil.areClassesEquivalent(cl1, cl2) || isInheritorDeep(cl1, cl2)

  private def isInheritorOrSame(tp: ScType, cl: PsiClass): Boolean = tp match {
    case ScCompoundType(comps, _, _) =>
      comps.exists(isInheritorOrSame(_, cl))
    case tpt: TypeParameterType =>
      isInheritorOrSame(tpt.upperType, cl)
    case _ =>
      tp.extractClass.exists(sameOrInheritor(_, cl))
  }

  private def isInheritorOrSelfOrSame(placeTd: ScTemplateDefinition, td: PsiClass): Boolean = {
    if (sameOrInheritor(placeTd, td)) return true

    placeTd.selfTypeElement match {
      case Some(te: ScSelfTypeElement) => te.typeElement match {
        case Some(te: ScTypeElement) =>
          te.`type`()
            .exists(isInheritorOrSame(_, td))
        case _ => false
      }
      case _ => false
    }
  }

  private def smartContextAncestor(td: ScTemplateDefinition, place: PsiElement, checkCompanion: Boolean): Boolean = {
    val withCompanion: Set[ScTemplateDefinition] =
      if (checkCompanion) getCompanionModule(td).toSet + td
      else Set(td)

    def withNavigationElem(t: ScTemplateDefinition): Set[ScTemplateDefinition] = t.getNavigationElement match {
      case `t` => Set(t)
      case other: ScTemplateDefinition => Set(t, other)
      case _ => Set(t)
    }

    val candidates = withCompanion.flatMap(withNavigationElem)
    place.withContexts.exists {
      case td: ScTemplateDefinition => candidates.contains(td)
      case _ => false
    }
  }

  private def packageContains(packageName: String, potentialChild: String): Boolean = {
    ScalaNamesUtil.equivalentFqn(potentialChild, packageName) || potentialChild.startsWith(packageName + ".")
  }

  def packageProcessDeclarations(pack: PsiPackage, processor: PsiScopeProcessor,
                                  state: ResolveState, lastParent: PsiElement, place: PsiElement): Boolean = {
    processor match {
      case b: BaseProcessor if b.isImplicitProcessor =>
        val objectsIterator = ScalaPsiManager.instance(pack.getProject).
          getPackageImplicitObjects(pack.getQualifiedName, place.resolveScope).iterator
        while (objectsIterator.hasNext) {
          val obj = objectsIterator.next()
          if (!processor.execute(obj, state)) return false
        }
        true
      case base: BaseProcessor =>
        val nameHint = base.getHint(NameHint.KEY)
        val name = if (nameHint == null) "" else nameHint.getName(state)
        if (name != null && name != "" && base.getClassKind) {
          try {
            base.setClassKind(classKind = false)

            if (base.getClassKindInner) {
              val manager = ScalaPsiManager.instance(pack.getProject)
              val qName = pack.getQualifiedName

              val calcForName = {
                val fqn = if (qName.length() > 0) qName + "." + name else name
                val scope = base match {
                  case r: ResolveProcessor => r.getResolveScope
                  case _ => place.resolveScope
                }
                val classes = manager.getCachedClasses(scope, fqn).iterator
                var stop = false
                while (classes.hasNext && !stop) {
                  val clazz = classes.next()
                  stop = clazz.containingClass == null && !processor.execute(clazz, state)
                }
                !stop
              }
              if (!calcForName) return false
            }

            //process subpackages
            if (base.kinds.contains(ResolveTargets.PACKAGE)) {
              val psiPack = pack match {
                case s: ScPackageImpl => s.pack
                case _ => pack
              }
              val qName: String = psiPack.getQualifiedName
              val subpackageQName: String = if (qName.isEmpty) name else qName + "." + name
              val subPackage = ScalaPsiManager.instance(psiPack.getProject).getCachedPackage(subpackageQName).orNull
              if (subPackage != null) {
                if (!processor.execute(subPackage, state)) return false
              }
              true
            } else true
          } finally {
            base.setClassKind(classKind = true)
          }
        } else {
          try {
            if (base.getClassKindInner) {
              base.setClassKind(classKind = false)
              val manager = ScalaPsiManager.instance(pack.getProject)
              val scope = base match {
                case r: ResolveProcessor => r.getResolveScope
                case _ => place.resolveScope
              }
              val iterator = manager.getClasses(pack, scope).iterator
              while (iterator.hasNext) {
                val clazz = iterator.next()
                if (clazz.containingClass == null && !processor.execute(clazz, state)) return false
              }
            }

            if (base.kinds.contains(ResolveTargets.PACKAGE)) {
              //process subpackages
              pack match {
                case s: ScPackageImpl =>
                  s.pack.processDeclarations(processor, state, lastParent, place)
                case _ =>
                  pack.processDeclarations(processor, state, lastParent, place)
              }
            } else true
          } finally {
            base.setClassKind(classKind = true)
          }
        }
      case _ => pack.processDeclarations(processor, state, lastParent, place)
    }
  }
}

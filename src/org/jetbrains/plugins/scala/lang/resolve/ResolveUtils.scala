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
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSelfTypeElement, ScTypeElement, ScTypeVariableTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAccessModifier, ScFieldId, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScSuperReference, ScThisReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.{ScSyntheticClass, ScSyntheticValue}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.impl.{ScPackageImpl, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil, types}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, ResolveProcessor, ResolverEnv}

import _root_.scala.collection.Set

/**
 * @author ven
 */
object ResolveUtils {
  def kindMatches(element: PsiElement, kinds: Set[ResolveTargets.Value]): Boolean = kinds == null ||
          (element match {
            case _: PsiPackage | _: ScPackaging => kinds contains PACKAGE
            case obj: ScObject if obj.isPackageObject => kinds contains PACKAGE
            case obj: ScObject => (kinds contains OBJECT) || (kinds contains METHOD)
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
              val parent = ScalaPsiUtil.getParentOfType(patt, classOf[ScVariable], classOf[ScValue])
              parent match {
                case x: ScVariable => kinds contains VAR
                case _ => kinds contains VAL
              }
            case patt: ScFieldId =>
              if (patt.getParent /*list of ids*/ .getParent.isInstanceOf[ScVariable])
                kinds contains VAR else kinds contains VAL
            case classParam: ScClassParameter =>
              if (classParam.isVar) kinds.contains(VAR) else kinds.contains(VAL)
            case param: ScParameter => kinds contains VAL
            case _: ScSelfTypeElement => kinds contains VAL
            case _: PsiMethod => kinds contains METHOD
            case _: ScFun => kinds contains METHOD
            case _: ScSyntheticValue => kinds contains VAL
            case f: PsiField => (kinds contains VAR) || (f.hasModifierPropertyScala(PsiModifier.FINAL) && kinds.contains(VAL))
            case _: PsiParameter => kinds contains VAL //to enable named Parameters resolve in Play 2.0 routing file for java methods
            case _ => false
          })

  def methodType(m : PsiMethod, s : ScSubstitutor, scope: GlobalSearchScope) =
    ScFunctionType(s.subst(m.getReturnType.toScType(m.getProject, scope)),
      m.getParameterList.getParameters.map({
        p => val pt = p.getType
        //scala hack: Objects in java are modelled as Any in scala
        if (pt.equalsToText("java.lang.Object")) types.Any
        else s.subst(pt.toScType(m.getProject, scope))
      }).toSeq)(m.getProject, scope)

  def javaMethodType(m: PsiMethod, s: ScSubstitutor, scope: GlobalSearchScope, returnType: Option[ScType] = None): ScMethodType = {
    val retType: ScType = (m, returnType) match {
      case (f: FakePsiMethod, None) => s.subst(f.retType)
      case (_, None) => s.subst(m.getReturnType.toScType(m.getProject, scope))
      case (_, Some(x)) => x
    }
    new ScMethodType(retType,
      m match {
        case f: FakePsiMethod => f.params.toSeq
        case _ =>
          m.getParameterList.getParameters.map { param =>
            val scType = s.subst(param.exactParamType())
            new Parameter("", None, scType, scType, false, param.isVarArgs, false, param.index, Some(param))
          }
      }, false)(m.getProject, scope)
  }

  def javaPolymorphicType(m: PsiMethod, s: ScSubstitutor, scope: GlobalSearchScope = null, returnType: Option[ScType] = None): NonValueType = {
    if (m.getTypeParameters.isEmpty) javaMethodType(m, s, scope, returnType)
    else {
      ScTypePolymorphicType(javaMethodType(m, s, scope, returnType), m.getTypeParameters.map(new TypeParameter(_)))
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
      case f: FakePsiMethod => f.navElement match {
        case memb: PsiMember => return isAccessible(memb, place)
        case _ =>
      }
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
        if (placeTd != null && !placeTd.isInstanceOf[ScTypeDefinition] && placeTd.extendsBlock.templateBody == None) {
          placeTd = getPlaceTd(placeTd)
        } else if (placeTd != null) {
          if (td != null && isInheritorOrSelfOrSame(placeTd, td)) return true
        }
        while (placeTd != null) {
          if (td == placeTd) return true
          val companion: ScTemplateDefinition = ScalaPsiUtil.getCompanionModule(placeTd).getOrElse(null: ScTemplateDefinition)
          if (companion != null && companion == td) return true
          placeTd = getPlaceTd(placeTd)
        }
        return false
      }
      while (placeTd != null) {
        if (td != null && isInheritorOrSelfOrSame(placeTd, td)) return true
        val companion: ScTemplateDefinition = ScalaPsiUtil.
                getCompanionModule(placeTd).getOrElse(null: ScTemplateDefinition)
        if (withCompanion && companion != null && td != null &&
                ScalaPsiUtil.cachedDeepIsInheritor(companion, td)) return true
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
              /*
              ScalaRefernce.pdf:
                A member M marked with this modifier can be accessed only from
                within the object in which it is defined.
              */
              place match {
                case ref: ScReferenceElement =>
                  ref.qualifier match {
                    case None =>
                      val enclosing = PsiTreeUtil.getContextOfType(scMember, true, classOf[ScTemplateDefinition])
                      if (enclosing == null) return true
                      return PsiTreeUtil.isContextAncestor(enclosing, place, false)
                    case Some(t: ScThisReference) =>
                      val enclosing = PsiTreeUtil.getContextOfType(scMember, true, classOf[ScTemplateDefinition])
                      if (enclosing == null) return true
                      t.refTemplate match {
                        case Some(t) => return t == enclosing
                        case _ => return PsiTreeUtil.isContextAncestor(enclosing, place, false)
                      }
                    case Some(ref: ScReferenceElement) =>
                      val enclosing = PsiTreeUtil.getContextOfType(scMember, true, classOf[ScTemplateDefinition])
                      if (enclosing == null) return false
                      val resolve = ref.resolve()
                      if (enclosing.extendsBlock.selfTypeElement == Some(resolve)) return true
                      else return false
                    case _ => return false
                  }
                case _ =>
                  val enclosing = PsiTreeUtil.getContextOfType(scMember, true, classOf[ScTemplateDefinition])
                  if (enclosing == null) return true
                  return PsiTreeUtil.isContextAncestor(enclosing, place, false)
              }
            }
            val ref = am.getReference
            if (ref != null) {
              val bind = ref.resolve
              if (bind == null) return true
              def processPackage(packageName: String): Boolean = {
                def context(place: PsiElement): PsiElement =
                  ScalaPsiUtil.getContextOfType(place, true, classOf[ScPackaging],
                    classOf[ScObject], classOf[ScalaFile])
                var placeEnclosing: PsiElement = context(place)
                while (placeEnclosing != null && placeEnclosing.isInstanceOf[ScObject] &&
                         !placeEnclosing.asInstanceOf[ScObject].isPackageObject)
                  placeEnclosing = context(placeEnclosing)
                if (placeEnclosing == null) return false //not Scala
                val placePackageName = placeEnclosing match {
                  case file: ScalaFile => ""
                  case obj: ScObject => obj.qualifiedName
                  case pack: ScPackaging => pack.fqn
                }
                packageContains(packageName, placePackageName)
              }
              bind match {
                case td: ScTemplateDefinition =>
                  PsiTreeUtil.isContextAncestor(td, place, false) ||
                          PsiTreeUtil.isContextAncestor(ScalaPsiUtil.getCompanionModule(td).getOrElse(null: PsiElement),
                            place, false) || (td.isInstanceOf[ScObject] &&
                          td.asInstanceOf[ScObject].isPackageObject && processPackage(td.qualifiedName))
                case pack: PsiPackage =>
                  val packageName = pack.getQualifiedName
                  processPackage(packageName)
                case _ => true
              }
            }
            else {
              /*
              ScalaRefernce.pdf:
                Such members can be accessed only from within the directly enclosing
                template and its companion module or companion class
              */
              val enclosing = ScalaPsiUtil.getContextOfType(scMember, true,
                classOf[ScalaFile], classOf[ScPackaging], classOf[ScTemplateDefinition])
              enclosing match {
                case td: ScTemplateDefinition =>
                  PsiTreeUtil.isContextAncestor(td, place, false) || PsiTreeUtil.isContextAncestor(ScalaPsiUtil.
                          getCompanionModule(td).getOrElse(null: PsiElement), place, false)
                case file: ScalaFile if file.isScriptFile() =>
                  PsiTreeUtil.isContextAncestor(file, place, false)
                case _ =>
                  val packageName = enclosing match {
                    case file: ScalaFile => ""
                    case packaging: ScPackaging => packaging.getPackageName
                    case _ => ""
                  }
                  val placeEnclosing: PsiElement = ScalaPsiUtil.
                          getContextOfType(place, true, classOf[ScPackaging], classOf[ScalaFile])
                  if (placeEnclosing == null) return false //not Scala
                  val placePackageName = placeEnclosing match {
                    case file: ScalaFile => ""
                    case pack: ScPackaging => pack.getPackageName
                  }
                  packageContains(packageName, placePackageName)
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
                  ScalaPsiUtil.getContextOfType(place, true, classOf[ScPackaging],
                    classOf[ScObject], classOf[ScalaFile])
                var placeEnclosing: PsiElement = context(place)
                while (placeEnclosing != null && placeEnclosing.isInstanceOf[ScObject] &&
                         !placeEnclosing.asInstanceOf[ScObject].isPackageObject)
                  placeEnclosing = context(placeEnclosing)
                if (placeEnclosing == null) return Some(false) //not Scala
                val placePackageName = placeEnclosing match {
                  case file: ScalaFile => ""
                  case obj: ScObject => obj.qualifiedName
                  case pack: ScPackaging => pack.fqn
                }
                if (packageContains(packageName, placePackageName)) return Some(true)
                None
              }
              bind match {
                case td: ScTemplateDefinition =>
                  if (PsiTreeUtil.isContextAncestor(td, place, false) || PsiTreeUtil.isContextAncestor(ScalaPsiUtil.
                          getCompanionModule(td).getOrElse(null: PsiElement), place, false)) return true
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
            val enclosing = ScalaPsiUtil.getContextOfType(scMember, true,
              classOf[ScalaFile], classOf[ScTemplateDefinition], classOf[ScPackaging])
            assert(enclosing != null, s"Enclosing is null in file ${scMember.getContainingFile.getName}:\n${scMember.getContainingFile.getText}")
            if (am.isThis) {
              place match {
                case ref: ScReferenceElement =>
                  ref.qualifier match {
                    case None =>
                    case Some(t: ScThisReference) =>
                    case Some(s: ScSuperReference) =>
                    case Some(ref: ScReferenceElement) =>
                      val enclosing = PsiTreeUtil.getContextOfType(scMember, true, classOf[ScTemplateDefinition])
                      if (enclosing == null) return false
                      val resolve = ref.resolve()
                      if (enclosing.extendsBlock.selfTypeElement != Some(resolve)) return false
                    case _ => return false
                  }
                case _ =>
              }
            }
            enclosing match {
              case td: ScTypeDefinition =>
                if (PsiTreeUtil.isContextAncestor(td, place, false) ||
                        (withCompanion && PsiTreeUtil.isContextAncestor(ScalaPsiUtil.getCompanionModule(td).
                                getOrElse(null: PsiElement), place, false))) return true
                checkProtected(td, withCompanion)
              case td: ScTemplateDefinition =>
                //it'd anonymous class, has access only inside
                PsiTreeUtil.isContextAncestor(td, place, false)
              case _ =>
                //same as for private
                val packageName = enclosing match {
                  case file: ScalaFile => ""
                  case packaging: ScPackaging => packaging.fullPackageName
                }
                val placeEnclosing: PsiElement = ScalaPsiUtil.
                        getContextOfType(place, true, classOf[ScPackaging], classOf[ScalaFile])
                if (placeEnclosing == null) return false //not Scala
                val placePackageName = placeEnclosing match {
                  case file: ScalaFile => ""
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
            case s: ScalaFile => ""
            case f: PsiClassOwner => f.getPackageName
            case _ => return false
          }
          val placeEnclosing: PsiElement = ScalaPsiUtil.
                  getContextOfType(place, true, classOf[ScPackaging], classOf[ScalaFile])
          if (placeEnclosing == null) return false
          val placePackageName = placeEnclosing match {
            case file: ScalaFile => ""
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
    val pack: ScPackaging = ScalaPsiUtil.getContextOfType(place, true, classOf[ScPackaging]) match {
      case pack: ScPackaging => pack
      case _ => null
    }
    if (pack == null) return ""
    pack.fullPackageName
  }

  private def isInheritorOrSelfOrSame(placeTd: ScTemplateDefinition, td: PsiClass): Boolean = {
    if (ScalaPsiUtil.cachedDeepIsInheritor(placeTd, td)) return true
    placeTd.selfTypeElement match {
      case Some(te: ScSelfTypeElement) => te.typeElement match {
        case Some(te: ScTypeElement) =>
          def isInheritorOrSame(tp: ScType): Boolean = {
            ScType.extractClass(tp) match {
              case Some(clazz) =>
                if (clazz == td) return true
                if (ScalaPsiUtil.cachedDeepIsInheritor(clazz, td)) return true
              case _ =>
            }
            false
          }
          te.getType(TypingContext.empty) match {
            case Success(ctp: ScCompoundType, _) =>
              for (tp <- ctp.components) {
                if (isInheritorOrSame(tp)) return true
              }
            case Success(tp: ScType, _) =>
              if (isInheritorOrSame(tp)) return true
            case _ =>
          }
        case _ =>
      }
      case _ =>
    }
    false
  }

  def packageContains(packageName: String, potentialChild: String): Boolean = {
    potentialChild == packageName || potentialChild.startsWith(packageName + ".")
  }

  def packageProcessDeclarations(pack: PsiPackage, processor: PsiScopeProcessor,
                                  state: ResolveState, lastParent: PsiElement, place: PsiElement): Boolean = {
    processor match {
      case b: BaseProcessor if b.isImplicitProcessor =>
        val objectsIterator = ScalaPsiManager.instance(pack.getProject).
          getPackageImplicitObjects(pack.getQualifiedName, place.getResolveScope).iterator
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
              def calcForName(name: String): Boolean = {
                val fqn = if (qName.length() > 0) qName + "." + name else name
                val scope = base match {
                  case r: ResolveProcessor => r.getResolveScope
                  case _ => place.getResolveScope
                }
                var classes: Array[PsiClass] = manager.getCachedClasses(scope, fqn)
                if (classes.isEmpty) {
                  //todo: fast fix for the problem with classes, should be fixed in indexes
                  val improvedFqn = fqn.split('.').map { s =>
                    if (ScalaNamesUtil.isKeyword(s)) s"`$s`" else s
                  }.mkString(".")
                  if (improvedFqn != fqn) {
                    classes = manager.getCachedClasses(scope, improvedFqn)
                  }
                }
                for (clazz <- classes if clazz.containingClass == null) {
                  if (!processor.execute(clazz, state)) return false
                }
                true
              }
              if (!calcForName(name)) return false
              val scalaName = { //todo: fast fix for the problem with classes, should be fixed in indexes
                base match {
                  case r: ResolveProcessor =>
                    val stateName = state.get(ResolverEnv.nameKey)
                    if (stateName == null) r.name else stateName
                  case _ => name
                }
              }
              if (scalaName != name && !calcForName(scalaName)) return false
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
                case _ => place.getResolveScope
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
package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.psi.util.PsiTreeUtil
import processor.{ImplicitProcessor, BaseProcessor}
import psi.api.base.{ScAccessModifier, ScFieldId}
import psi.api.ScalaFile
import psi.api.toplevel.typedef._
import psi.impl.toplevel.typedef.TypeDefinitionMembers
import psi.types._
import _root_.scala.collection.Set
import nonvalue._
import com.intellij.psi._
import psi.api.base.patterns.ScBindingPattern
import psi.api.toplevel.packaging.ScPackaging
import ResolveTargets._
import psi.api.statements._
import com.intellij.codeInsight.lookup._
import params.{ScClassParameter, ScParameter, ScTypeParam}
import psi.{ScalaPsiUtil, ScalaPsiElement}
import psi.api.toplevel.ScTypeParametersOwner
import psi.impl.toplevel.synthetic.{ScSyntheticClass, ScSyntheticValue}
import com.intellij.openapi.application.ApplicationManager
import result.{TypingContextOwner, Success, TypingContext}
import scope.{NameHint, PsiScopeProcessor}
import search.GlobalSearchScope
import psi.api.expr.ScSuperReference
import java.lang.String
import com.intellij.lang.StdLanguages
import com.intellij.psi.impl.source.resolve.JavaResolveUtil
import psi.fake.FakePsiMethod
import completion.handlers.{ScalaClassNameInsertHandler, ScalaInsertHandler}
import psi.api.base.types.{ScTypeElement, ScSelfTypeElement}
import com.intellij.openapi.util.Key
import psi.impl.ScalaPsiManager

/**
 * @author ven
 */
object ResolveUtils {
  def kindMatches(element: PsiElement, kinds: Set[ResolveTargets.Value]): Boolean = kinds == null ||
          (element match {
            case _: PsiPackage | _: ScPackaging => kinds contains PACKAGE
            case obj: ScObject if obj.isPackageObject => kinds contains PACKAGE
            case obj: ScObject => (kinds contains OBJECT) || (kinds contains METHOD)
            case _: ScTypeParam => kinds contains CLASS
            case _: ScTypeAlias => kinds contains CLASS
            case _: ScTypeDefinition => kinds contains CLASS
            case _: ScSyntheticClass => kinds contains CLASS
            case c: PsiClass => {
              if (kinds contains CLASS) true
              else {
                def isStaticCorrect(clazz: PsiClass): Boolean = {
                  val cclazz = clazz.getContainingClass
                  cclazz == null || (clazz.hasModifierProperty(PsiModifier.STATIC) && isStaticCorrect(cclazz))
                }
                (kinds contains OBJECT) && isStaticCorrect(c)
              }
            }
            case patt: ScBindingPattern => {
              val parent = ScalaPsiUtil.getParentOfType(patt, classOf[ScVariable], classOf[ScValue])
              parent match {
                case x: ScVariable => kinds contains VAR
                case _ => kinds contains VAL
              }
            }
            case patt: ScFieldId => {
              if (patt.getParent /*list of ids*/ .getParent.isInstanceOf[ScVariable])
                kinds contains VAR else kinds contains VAL
            }
            case classParam: ScClassParameter =>
              if (classParam.isVar) kinds.contains(VAR) else kinds.contains(VAL)
            case param: ScParameter => kinds contains VAL
            case _: ScSelfTypeElement => kinds contains VAL
            case _: PsiMethod => kinds contains METHOD
            case _: ScFun => kinds contains METHOD
            case _: ScSyntheticValue => kinds contains VAL
            case f: PsiField => (kinds contains VAR) || (f.hasModifierProperty(PsiModifier.FINAL) && kinds.contains(VAL))
            case _ => false
          })

  def methodType(m : PsiMethod, s : ScSubstitutor, scope: GlobalSearchScope) =
    new ScFunctionType(s.subst(ScType.create(m.getReturnType, m.getProject, scope)),
      collection.immutable.Seq(m.getParameterList.getParameters.map({
        p => val pt = p.getType
        //scala hack: Objects in java are modelled as Any in scala
        if (pt.equalsToText("java.lang.Object")) Any
        else s.subst(ScType.create(pt, m.getProject, scope))
      }).toSeq: _*))(m.getProject, scope)

  def javaMethodType(m: PsiMethod, s: ScSubstitutor, scope: GlobalSearchScope, returnType: Option[ScType] = None): ScMethodType = {
    val retType: ScType = (m, returnType) match {
      case (f: FakePsiMethod, None) => s.subst(f.retType)
      case (_, None) => s.subst(ScType.create(m.getReturnType, m.getProject, scope))
      case (_, Some(x)) => x
    }
    new ScMethodType(retType, m.getParameterList.getParameters.map((param: PsiParameter) => {
      var psiType = param.getType
      if (param.isVarArgs && psiType.isInstanceOf[PsiArrayType]) {
        psiType = psiType.asInstanceOf[PsiArrayType].getComponentType
      }
      new Parameter("", s.subst(ScType.create(psiType, m.getProject, scope, paramTopLevel = true)), false, param.isVarArgs, false)
    }).toSeq, false)(m.getProject, scope)
  }

  def javaPolymorphicType(m: PsiMethod, s: ScSubstitutor, scope: GlobalSearchScope = null, returnType: Option[ScType] = None): NonValueType = {
    if (m.getTypeParameters.length == 0) javaMethodType(m, s, scope, returnType)
    else {
      ScTypePolymorphicType(javaMethodType(m, s, scope, returnType), m.getTypeParameters.map(tp =>
        TypeParameter(tp.getName, Nothing, Any, tp))) //todo: add lower and upper bounds
    }
  }

  def isAccessible(memb: PsiMember, place: PsiElement): Boolean = {
    if (place.getLanguage == StdLanguages.JAVA) {
      return JavaResolveUtil.isAccessible(memb, memb.getContainingClass, memb.getModifierList, place, null, null)
    }

    import ScalaPsiUtil.getPlaceTd
    //this is to make place and member on same level (resolve from library source)
    var member: PsiMember = memb
    memb.getContainingFile match {
      case file: ScalaFile if file.isCompiled => {
        place.getContainingFile match {
          case file: ScalaFile if file.isCompiled =>
          case _ => member = memb.getOriginalElement.asInstanceOf[PsiMember]
        }
      }
      case _ =>
    }

    member match {
      case f: ScFunction if f.isBridge => return false
      case _ =>
    }

    if (member.hasModifierProperty("public")) return true


    member match {
      case scMember: ScMember => scMember.getModifierList.accessModifier match {
        case None => true
        case Some(am: ScAccessModifier) => {
          if (am.isPrivate) {
            if (am.access == am.Access.THIS_PRIVATE) {
              /*
              ScalaRefernce.pdf:
                A member M marked with this modifier can be accessed only from
                within the object in which it is defined.
              */
              val enclosing = PsiTreeUtil.getContextOfType(scMember, false, classOf[ScTemplateDefinition])
              if (enclosing == null) return true
              return PsiTreeUtil.isContextAncestor(enclosing, place, false)
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
                  case file: ScalaFile => file.getPackageName
                  case obj: ScObject => obj.getQualifiedName
                  case pack: ScPackaging => pack.fqn
                }
                packageContains(packageName, placePackageName)
              }
              bind match {
                case td: ScTemplateDefinition => {
                  PsiTreeUtil.isContextAncestor(td, place, false) ||
                          PsiTreeUtil.isContextAncestor(ScalaPsiUtil.getCompanionModule(td).getOrElse(null: PsiElement),
                            place, false) || (td.isInstanceOf[ScObject] &&
                          td.asInstanceOf[ScObject].isPackageObject && processPackage(td.getQualifiedName))
                }
                case pack: PsiPackage => {
                  val packageName = pack.getQualifiedName
                  processPackage(packageName)
                }
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
                case td: ScTemplateDefinition => {
                  PsiTreeUtil.isContextAncestor(td, place, false) || PsiTreeUtil.isContextAncestor(ScalaPsiUtil.
                          getCompanionModule(td).getOrElse(null: PsiElement), place, false)
                }
                case file: ScalaFile if file.isScriptFile() => {
                  PsiTreeUtil.isContextAncestor(file, place, false)
                }
                case _ => {
                  val packageName = enclosing match {
                    case file: ScalaFile => file.getPackageName
                    case packaging: ScPackaging => packaging.getPackageName
                  }
                  val placeEnclosing: PsiElement = ScalaPsiUtil.
                          getContextOfType(place, true, classOf[ScPackaging], classOf[ScalaFile])
                  if (placeEnclosing == null) return false //not Scala
                  val placePackageName = placeEnclosing match {
                    case file: ScalaFile => file.getPackageName
                    case pack: ScPackaging => pack.getPackageName
                  }
                  packageContains(packageName, placePackageName)
                }
              }
            }
          } else if (am.isProtected) { //todo: it's wrong if reference after not appropriate class type
            val withCompanion = am.access != am.Access.THIS_PROTECTED
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
                  case file: ScalaFile => file.getPackageName
                  case obj: ScObject => obj.getQualifiedName
                  case pack: ScPackaging => pack.fqn
                }
                if (packageContains(packageName, placePackageName)) return Some(true)
                None
              }
              bind match {
                case td: ScTemplateDefinition => {
                  if (PsiTreeUtil.isContextAncestor(td, place, false) || PsiTreeUtil.isContextAncestor(ScalaPsiUtil.
                          getCompanionModule(td).getOrElse(null: PsiElement), place, false)) return true
                  td match {
                    case o: ScObject if o.isPackageObject =>
                      processPackage(o.getQualifiedName) match {
                        case Some(x) => return x
                        case None =>
                      }
                    case _ =>
                  }
                }
                case pack: PsiPackage => { //like private (nothing related to real life)
                  val packageName = pack.getQualifiedName
                  processPackage(packageName) match {
                    case Some(x) => return x
                    case None =>
                  }
                }
                case _ => return true
              }
            }
            val enclosing = ScalaPsiUtil.getContextOfType(scMember, true,
              classOf[ScalaFile], classOf[ScTemplateDefinition], classOf[ScPackaging])
            enclosing match {
              case td: ScTypeDefinition => {
                if (PsiTreeUtil.isContextAncestor(td, place, false) ||
                        (withCompanion && PsiTreeUtil.isContextAncestor(ScalaPsiUtil.getCompanionModule(td).
                                getOrElse(null: PsiElement), place, false))) return true
                val isConstr = member match {case m: PsiMethod => m.isConstructor case _ => false}
                var placeTd: ScTemplateDefinition = getPlaceTd(place, isConstr)
                if (isConstr) {
                  if (placeTd != null && !placeTd.isInstanceOf[ScTypeDefinition]) {
                    placeTd = getPlaceTd(placeTd)
                  } else if (placeTd !=  null) {
                    if (isInheritorOrSelfOrSame(placeTd, td)) return true
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
                  if (isInheritorOrSelfOrSame(placeTd, td)) return true
                  val companion: ScTemplateDefinition = ScalaPsiUtil.
                          getCompanionModule(placeTd).getOrElse(null: ScTemplateDefinition)
                  if (withCompanion && companion != null && companion.isInheritor (td, true)) return true
                  placeTd = getPlaceTd(placeTd)
                }
                false
              }
              case td: ScTemplateDefinition => {
                //it'd anonymous class, has access only inside
                PsiTreeUtil.isContextAncestor(td, place, false)
              }
              case _ => {
                //same as for private
                val packageName = enclosing match {
                  case file: ScalaFile => file.getPackageName
                  case packaging: ScPackaging => packaging.fullPackageName
                }
                val placeEnclosing: PsiElement = ScalaPsiUtil.
                        getContextOfType(place, true, classOf[ScPackaging], classOf[ScalaFile])
                if (placeEnclosing == null) return false //not Scala
                val placePackageName = placeEnclosing match {
                  case file: ScalaFile => file.getPackageName
                  case pack: ScPackaging => pack.fullPackageName
                }
                packageContains(packageName, placePackageName)
              }
            }
          } else true
        }
      }
      case _ => {
        if (member.hasModifierProperty("public")) true
        else if (member.hasModifierProperty("private")) false
        else if (member.hasModifierProperty("protected")) {
          val clazz = member.getContainingClass
          val isConstr = member match {case m: PsiMethod => m.isConstructor case _ => false}
          var placeTd = getPlaceTd(place, isConstr)
          if (isConstr) {
            if (placeTd != null && !placeTd.isInstanceOf[ScTypeDefinition]) {
              placeTd = getPlaceTd(placeTd)
            } else if (placeTd !=  null) {
              if (isInheritorOrSelfOrSame(placeTd, clazz)) return true
            }
            while (placeTd != null) {
              if (clazz == placeTd) return true
              val companion: ScTemplateDefinition = ScalaPsiUtil.getCompanionModule(placeTd).getOrElse(null: ScTemplateDefinition)
              if (companion != null && companion == clazz) return true
              placeTd = getPlaceTd(placeTd)
            }
            return false
          }
          while (placeTd != null) {
            if (isInheritorOrSelfOrSame(placeTd, clazz)) return true
            val companion: ScTemplateDefinition = ScalaPsiUtil.getCompanionModule(placeTd).getOrElse(null: ScTemplateDefinition)
            if (companion != null && companion.isInheritor (clazz, true)) return true
            placeTd = getPlaceTd(placeTd)
          }
          false
        } else {
          val packageName = member.getContainingFile match {
            case f: PsiClassOwner => f.getPackageName
            case _ => return false
          }
          val placeEnclosing: PsiElement = ScalaPsiUtil.
                  getContextOfType(place, true, classOf[ScPackaging], classOf[ScalaFile])
          if (placeEnclosing == null) return false
          val placePackageName = placeEnclosing match {
            case file: ScalaFile => file.getPackageName
            case pack: ScPackaging => pack.fullPackageName
          }
          packageContains(packageName, placePackageName)
        }
      }
    }
  }

  def processSuperReference(superRef: ScSuperReference, processor : BaseProcessor, place : ScalaPsiElement) = superRef.staticSuper match {
    case Some(t) => processor.processType(t, place)
    case None => superRef.drvTemplate match {
      case Some(c) => {
        TypeDefinitionMembers.processSuperDeclarations(c, processor, ResolveState.initial.put(ScSubstitutor.key, ScSubstitutor.empty), null, place)
      }
      case None =>
    }
  }

  /**
   * Important! Do not change return signature. Because of bad architecture this change can cause errors on runtime.
   */
  def getLookupElement(resolveResult: ScalaResolveResult,
                       qualifierType: ScType = Nothing,
                       isClassName: Boolean = false,
                       isInImport: Boolean = false,
                       isOverloadedForClassName: Boolean = false,
                       shouldImport: Boolean = false): Seq[(LookupElement, PsiElement, ScSubstitutor)] = {
    import org.jetbrains.plugins.scala.lang.psi.PresentationUtil.presentationString
    val element = resolveResult.element
    val substitutor = resolveResult.substitutor
    val isRenamed: Option[String] = resolveResult.isRenamed match {
      case Some(x) if element.getName != x => Some(x)
      case _ => None
    }

    def getLookupElementInternal(isAssignment: Boolean,
                                 name: String): (LookupElement, PsiNamedElement, ScSubstitutor) = {
      var lookupBuilder: LookupElementBuilder =
        LookupElementBuilder.create(element, name) //don't add elements to lookup
      lookupBuilder = lookupBuilder.setInsertHandler(
        if (isClassName) new ScalaClassNameInsertHandler else new ScalaInsertHandler
      )
      val containingClass = ScalaPsiUtil.nameContext(element) match {
        case memb: PsiMember => memb.getContainingClass
        case _ => null
      }
      var isBold = false
      var isDeprecated = false
      ScType.extractDesignated(qualifierType) match {
        case Some((named, _)) => {
          val clazz: PsiClass = named match {
            case cl: PsiClass => cl
            case tp: TypingContextOwner => tp.getType(TypingContext.empty).map(ScType.extractClass(_)) match {
              case Success(Some(cl), _) => cl
              case _ => null
            }
            case _ => null
          }
          if (clazz != null)
            element match {
              case m: PsiMember => {
                if (m.getContainingClass == clazz) isBold = true
              }
              case _ =>
            }
        }
        case _ =>
      }
      val isUnderlined = resolveResult.implicitFunction != None
      element match {
        case doc: PsiDocCommentOwner if doc.isDeprecated => isDeprecated = true
        case _ =>
      }
      lookupBuilder = lookupBuilder.setRenderer(new LookupElementRenderer[LookupElement] {
        def renderElement(ignore: LookupElement, presentation: LookupElementPresentation) {
          val tailText: String = element match {
            case t: ScFun => {
              if (t.typeParameters.length > 0) t.typeParameters.map(param => presentationString(param, substitutor)).
                mkString("[", ", ", "]")
              else ""
            }
            case t: ScTypeParametersOwner => {
              t.typeParametersClause match {
                case Some(tp) => presentationString(tp, substitutor)
                case None => ""
              }
            }
            case p: PsiTypeParameterListOwner if p.getTypeParameters.length > 0 => {
              p.getTypeParameters.map(ptp => presentationString(ptp)).mkString("[", ", ", "]")
            }
            case _ => ""
          }
          element match {
            //scala
            case fun: ScFunction => {
              presentation.setTypeText(presentationString(fun.returnType.getOrAny, substitutor))
              val tailText1 = if (isAssignment) {
                " = " + presentationString(fun.paramClauses, substitutor)
              } else {
                tailText + (
                  if (!isOverloadedForClassName) presentationString(fun.paramClauses, substitutor)
                  else "(...)"
                ) + (
                  if (shouldImport && isClassName && containingClass != null)
                    " " + containingClass.getPresentation.getLocationString
                  else if (isClassName && containingClass != null)
                    " in " + containingClass.getName + " " + containingClass.getPresentation.getLocationString
                  else ""
                  )
              }
              presentation.setTailText(tailText1)
            }
            case fun: ScFun => {
              presentation.setTypeText(presentationString(fun.retType, substitutor))
              val paramClausesText = fun.paramClauses.map(_.map(presentationString(_, substitutor)).
                mkString("(", ", ", ")")).mkString
              presentation.setTailText(tailText + paramClausesText)
            }
            case bind: ScBindingPattern => {
              presentation.setTypeText(presentationString(bind.getType(TypingContext.empty).getOrAny, substitutor))
            }
            case f: ScFieldId => {
              presentation.setTypeText(presentationString(f.getType(TypingContext.empty).getOrAny, substitutor))
            }
            case param: ScParameter => {
              val str: String =
                presentationString(param.getRealParameterType(TypingContext.empty).getOrAny, substitutor)
              if (resolveResult.isNamedParameter) {
                presentation.setTailText(" = " + str)
              } else {
                presentation.setTypeText(str)
              }
            }
            case clazz: PsiClass => {
              val location: String = clazz.getPresentation.getLocationString
              presentation.setTailText(tailText + " " + location, true)
            }
            case alias: ScTypeAliasDefinition => {
              presentation.setTypeText(presentationString(alias.aliasedType.getOrAny, substitutor))
            }
            case method: PsiMethod => {
              val str: String = presentationString(method.getReturnType, substitutor)
              if (resolveResult.isNamedParameter) {
                presentation.setTailText(" = " + str)
              } else {
                presentation.setTypeText(str)
                val params =
                  if (!isOverloadedForClassName) presentationString(method.getParameterList, substitutor)
                  else "(...)"
                val tailText1 = tailText + params + (
                  if (shouldImport && isClassName && containingClass != null)
                    " " + containingClass.getPresentation.getLocationString
                  else if (isClassName && containingClass != null)
                    " in " + containingClass.getName + " " + containingClass.getPresentation.getLocationString
                  else ""
                  )
                presentation.setTailText(tailText1)
              }
            }
            case f: PsiField => {
              presentation.setTypeText(presentationString(f.getType, substitutor))
            }
            case _ =>
          }
          if (presentation.isReal)
            presentation.setIcon(element.getIcon(0))
          var itemText: String =
            if (isRenamed == None) if (isClassName && shouldImport) {
              val containingClass = ScalaPsiUtil.nameContext(element) match {
                case memb: PsiMember => memb.getContainingClass
                case _ => null
              }
              if (containingClass != null) containingClass.getName + "." + name
              else name
            } else name
            else name + "<=" + element.getName
          val someKey = Option(ignore.getUserData(someSmartCompletionKey)).map(_.booleanValue()).getOrElse(false)
          if (someKey) itemText = "Some(" + itemText + ")"
          presentation.setItemText(itemText)
          presentation.setStrikeout(isDeprecated)
          presentation.setItemTextBold(isBold)
          if (ScalaPsiUtil.getSettings(element.getProject).SHOW_IMPLICIT_CONVERSIONS)
            presentation.setItemTextUnderlined(isUnderlined)
        }
      })
      val returnLookupElement: LookupElement = lookupBuilder
      returnLookupElement.putUserData(isInImportKey, new java.lang.Boolean(isInImport))
      returnLookupElement.putUserData(isNamedParameterOrAssignment,
        new java.lang.Boolean(resolveResult.isNamedParameter || isAssignment))
      returnLookupElement.putUserData(isBoldKey, new java.lang.Boolean(isBold))
      returnLookupElement.putUserData(isUnderlinedKey, new java.lang.Boolean(isUnderlined))
      returnLookupElement.putUserData(shouldImportKey, new java.lang.Boolean(shouldImport))
      returnLookupElement.putUserData(classNameKey, new java.lang.Boolean(isClassName))

      (returnLookupElement, element, substitutor)
    }

    val name: String = isRenamed.getOrElse(element.getName)
    val Setter = """(.*)_=""".r
    name match {
      case Setter(prefix) =>
        Seq(getLookupElementInternal(true, prefix), getLookupElementInternal(false, name))
      case _ => Seq(getLookupElementInternal(false, name))
    }
  }

  val isNamedParameterOrAssignment: Key[java.lang.Boolean] = Key.create("is.named.parameter.or.assignment.key")
  val isBoldKey: Key[java.lang.Boolean] = Key.create("is.bold.key")
  val isUnderlinedKey: Key[java.lang.Boolean] = Key.create("is.underlined.key")
  val shouldImportKey: Key[java.lang.Boolean] = Key.create("should.import.key")
  val usedImportStaticQuickfixKey: Key[java.lang.Boolean] = Key.create("used.import.static.quickfix.key")
  val classNameKey: Key[java.lang.Boolean] = Key.create("class.name.key")
  val isInImportKey: Key[java.lang.Boolean] = Key.create("is.in.import.key")
  val typeParametersProblemKey: Key[java.lang.Boolean] = Key.create("type.parameters.problem.key")
  val typeParametersKey: Key[Seq[ScType]] = Key.create("type.parameters.key")
  val someSmartCompletionKey: Key[java.lang.Boolean] = Key.create("some.smart.completion.key")

  case class ScalaLookupObject(elem: PsiNamedElement, isNamedParameter: Boolean, isInImport: Boolean) {
    private var typeParameters: Seq[ScType] = Seq.empty
    var typeParametersProblem: Boolean = false

    def setTypeParameters(a: Seq[ScType]) {
      typeParameters = a
    }
    def getTypeParameters: Seq[ScType] = typeParameters
  }

  def getPlacePackage(place: PsiElement): String = {
    val pack: ScPackaging = ScalaPsiUtil.getParentOfType(place, classOf[ScPackaging]) match {
      case pack: ScPackaging => pack
      case _ => null
    }
    if (pack == null) return ""
    pack.fullPackageName
  }

  private def isInheritorOrSelfOrSame(placeTd: ScTemplateDefinition, td: PsiClass): Boolean = {
    if (placeTd.isInheritor(td, true)) return true
    placeTd.selfTypeElement match {
      case Some(te: ScSelfTypeElement) => te.typeElement match {
        case Some(te: ScTypeElement) => {
          def isInheritorOrSame(tp: ScType): Boolean = {
            ScType.extractClass(tp) match {
              case Some(clazz) => {
                if (clazz == td) return true
                if (clazz.isInheritor(td, true)) return true
              }
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
      case impl: ImplicitProcessor =>
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
            base.setClassKind(false)

            val manager = ScalaPsiManager.instance(pack.getProject)
            val qName = pack.getQualifiedName
            val fqn = if (qName.length() > 0) qName + "." + name else name
            val classes: Array[PsiClass] = manager.getCachedClasses(place.getResolveScope, fqn)
            for (clazz <- classes) {
              if (!processor.execute(clazz, state)) return false
            }
            
            //process subpackages
            pack.processDeclarations(processor, state, lastParent, place)
          } finally {
            base.setClassKind(true)
          }
        } else pack.processDeclarations(processor, state, lastParent, place)
      case _ => pack.processDeclarations(processor, state, lastParent, place)
    }
  }
}
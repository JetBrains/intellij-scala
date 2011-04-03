package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.psi.util.PsiTreeUtil
import lexer.ScalaTokenTypes
import processor.BaseProcessor
import psi.api.base.{ScStableCodeReferenceElement, ScAccessModifier, ScFieldId}
import psi.api.ScalaFile
import psi.api.toplevel.typedef._
import psi.impl.toplevel.typedef.TypeDefinitionMembers
import psi.types._
import _root_.scala.collection.Set
import nonvalue._
import psi.api.statements.params.{ScParameter, ScTypeParam}
import com.intellij.psi._
import psi.api.base.patterns.ScBindingPattern
import psi.api.toplevel.packaging.ScPackaging
import ResolveTargets._
import psi.api.statements._
import com.intellij.codeInsight.lookup._
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import icons.Icons
import psi.{PresentationUtil, ScalaPsiUtil, ScalaPsiElement}
import psi.api.toplevel.{ScTypeParametersOwner, ScModifierListOwner}
import psi.impl.toplevel.synthetic.{ScSyntheticTypeParameter, ScSyntheticClass, ScSyntheticValue}
import psi.api.base.types.{ScTypeElement, ScSelfTypeElement}
import result.{Success, TypingContext}
import com.intellij.psi.impl.compiled.ClsParameterImpl
import com.intellij.openapi.application.{ApplicationManager, Application}
import search.GlobalSearchScope
import psi.api.expr.{ScNewTemplateDefinition, ScSuperReference}
import java.lang.String
import com.intellij.lang.StdLanguages
import com.intellij.psi.impl.source.resolve.JavaResolveUtil
import psi.fake.FakePsiMethod
import completion.handlers.{ScalaClassNameInsertHandler, ScalaInsertHandler}

/**
 * @author ven
 */
object ResolveUtils {
  def kindMatches(element: PsiElement, kinds: Set[ResolveTargets.Value]): Boolean = kinds == null ||
          (element match {
            case _: PsiPackage => kinds contains PACKAGE
            case _: ScPackaging => kinds contains PACKAGE
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
              if (patt.getParent /*list of ids*/ .getParent.isInstanceOf[ScVariable])
                kinds contains VAR else kinds contains VAL
            }
            case patt: ScFieldId => {
              if (patt.getParent /*list of ids*/ .getParent.isInstanceOf[ScVariable])
                kinds contains VAR else kinds contains VAL
            }
            case _: ScParameter => kinds contains VAL
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
      Parameter("", s.subst(ScType.create(psiType, m.getProject, scope, paramTopLevel = true)), false, param.isVarArgs, false)
    }).toSeq, false)(m.getProject, scope)
  }

  def javaPolymorphicType(m: PsiMethod, s: ScSubstitutor, scope: GlobalSearchScope = null, returnType: Option[ScType] = None): NonValueType = {
    if (m.getTypeParameters.length == 0) return javaMethodType(m, s, scope, returnType)
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
                return placePackageName.startsWith(packageName)
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
                  return processPackage(packageName)
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
                  return placePackageName.startsWith(packageName)
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
                if (placePackageName.startsWith(packageName)) return Some(true)
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
                var placeTd: ScTemplateDefinition = getPlaceTd(place)
                while (placeTd != null) {
                  if (placeTd.isInheritor(td, true)) return true
                  placeTd.selfTypeElement match {
                    case Some(te: ScSelfTypeElement) => te.typeElement match {
                      case Some(te: ScTypeElement) => {
                        te.getType(TypingContext.empty) match {
                          case Success(tp: ScType, _) => ScType.extractClass(tp) match {
                            case Some(clazz) => {
                              if (clazz == td) return true
                              if (clazz.isInheritor(td, true)) return true
                            }
                            case _ =>
                          }
                          case _ =>
                        }
                      }
                      case _ =>
                    }
                    case _ =>
                  }
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
                return placePackageName.startsWith(packageName)
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
          var placeTd: ScTemplateDefinition = getPlaceTd(place)
          while (placeTd != null) {
            if (placeTd.isInheritor(clazz, true)) return true
            placeTd.selfTypeElement match {
              case Some(te: ScSelfTypeElement) => te.typeElement match {
                case Some(te: ScTypeElement) => {
                  te.getType(TypingContext.empty) match {
                    case Success(tp: ScType, _) => ScType.extractClass(tp) match {
                      case Some(cl) => {
                        if (cl == clazz) return true
                        if (cl.isInheritor(clazz, true)) return true
                      }
                      case _ =>
                    }
                    case _ =>
                  }
                }
                case _ =>
              }
              case _ =>
            }
            val companion: ScTemplateDefinition = ScalaPsiUtil.
                    getCompanionModule(placeTd).getOrElse(null: ScTemplateDefinition)
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
          return placePackageName.startsWith(packageName)
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

  def getLookupElement(resolveResult: ScalaResolveResult,
                       qualifierType: ScType = Nothing,
                       isClassName: Boolean = false): (LookupElement, PsiElement, ScSubstitutor) = {
    import PresentationUtil.presentationString
    val element = resolveResult.element
    val substitutor = resolveResult.substitutor
    val isRenamed: Option[String] = resolveResult.isRenamed match {
      case Some(x) if element.getName != x => Some(x)
      case _ => None
    }

    val name: String = isRenamed.getOrElse(element.getName)
    var lookupBuilder: LookupElementBuilder =
      LookupElementBuilder.create(ScalaLookupObject(element, resolveResult.isNamedParameter), name) //don't add elements to lookup
    lookupBuilder = lookupBuilder.setInsertHandler(
      if (isClassName) new ScalaClassNameInsertHandler else new ScalaInsertHandler
    )
    lookupBuilder = lookupBuilder.setRenderer(new LookupElementRenderer[LookupElement] {
      def renderElement(ignore: LookupElement, presentation: LookupElementPresentation): Unit = {
        var isBold = false
        var isDeprecated = false
        ScType.extractClass(qualifierType) match {
          case Some(clazz) =>  {
            element match {
              case m: PsiMember  => {
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
        val tailText: String = element match {
          case t: ScFun => {
            if (t.typeParameters.length > 0) t.typeParameters.map(param => presentationString(param, substitutor)).mkString("[", ", ", "]")
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
            presentation.setTypeText(presentationString(fun.returnType.getOrElse(Any), substitutor))
            presentation.setTailText(tailText + presentationString(fun.paramClauses, substitutor))
          }
          case fun: ScFun => {
            presentation.setTypeText(presentationString(fun.retType, substitutor))
            val paramClausesText = fun.paramClauses.map(_.map(presentationString(_, substitutor)).mkString("(", ", ", ")")).mkString
            presentation.setTailText(tailText + paramClausesText)
          }
          case bind: ScBindingPattern => {
            presentation.setTypeText(presentationString(bind.getType(TypingContext.empty).getOrElse(Any), substitutor))
          }
          case f: ScFieldId => {
            presentation.setTypeText(presentationString(f.getType(TypingContext.empty).getOrElse(Any), substitutor))
          }
          case param: ScParameter => {
            val str: String = presentationString(param.getRealParameterType(TypingContext.empty).getOrElse(Any), substitutor)
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
            presentation.setTypeText(presentationString(alias.aliasedType.getOrElse(Any), substitutor))
          }
          case method: PsiMethod => {
            val str: String = presentationString(method.getReturnType, substitutor)
            if (resolveResult.isNamedParameter) {
              presentation.setTailText(" = " + str)
            } else {
              presentation.setTypeText(str)
              presentation.setTailText(tailText + presentationString(method.getParameterList, substitutor))
            }
          }
          case f: PsiField => {
            presentation.setTypeText(presentationString(f.getType, substitutor))
          }
          case _ =>
        }
        presentation.setIcon(element.getIcon(0))
        presentation.setItemText(name + (if (isRenamed == None) "" else " <= " + element.getName))
        presentation.setStrikeout(isDeprecated)
        presentation.setItemTextBold(isBold)
        if (ScalaPsiUtil.getSettings(element.getProject).SHOW_IMPLICIT_CONVERSIONS)
          presentation.setItemTextUnderlined(isUnderlined)
      }
    })
    val returnLookupElement =
      if (ApplicationManager.getApplication.isUnitTestMode) AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(lookupBuilder)  
      else lookupBuilder
    (returnLookupElement, element, substitutor)
  }

  case class ScalaLookupObject(elem: PsiNamedElement, isNamedParameter: Boolean)

  def getPlacePackage(place: PsiElement): String = {
    val pack: ScPackaging = ScalaPsiUtil.getParentOfType(place, classOf[ScPackaging]) match {
      case pack: ScPackaging => pack
      case _ => null
    }
    if (pack == null) return ""
    pack.fullPackageName
  }
}
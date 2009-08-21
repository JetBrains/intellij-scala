package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.psi.util.PsiTreeUtil
import lexer.ScalaTokenTypes
import psi.api.base.{ScStableCodeReferenceElement, ScAccessModifier, ScFieldId}
import psi.api.expr.ScSuperReference
import psi.api.ScalaFile
import psi.api.toplevel.typedef._
import psi.impl.toplevel.typedef.TypeDefinitionMembers
import psi.types._
import _root_.scala.collection.Set
import psi.api.statements.params.{ScParameter, ScTypeParam}
import com.intellij.psi._
import psi.api.base.patterns.ScBindingPattern
import psi.api.toplevel.packaging.ScPackaging
import ResolveTargets._
import psi.api.statements._
import completion.handlers.ScalaInsertHandler
import com.intellij.codeInsight.lookup._
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import icons.Icons
import psi.{PresentationUtil, ScalaPsiUtil, ScalaPsiElement}
import psi.api.toplevel.{ScTypeParametersOwner, ScModifierListOwner}
import psi.impl.toplevel.synthetic.{ScSyntheticTypeParameter, ScSyntheticClass, ScSyntheticValue}
import psi.api.base.types.ScSelfTypeElement

/**
 * @author ven
 */
object ResolveUtils {
  def kindMatches(element: PsiElement, kinds: Set[ResolveTargets.Value]): Boolean = kinds == null ||
          (element match {
            case _: PsiPackage => kinds contains PACKAGE
            case _: ScPackaging => kinds contains PACKAGE
            case obj: ScObject => (kinds contains OBJECT) || (kinds contains METHOD)
            case c: ScClass if c.isCase => (kinds contains OBJECT) || (kinds contains CLASS) || (kinds contains METHOD)
            case _: ScTypeParam => kinds contains CLASS
            case _: ScTypeAlias => kinds contains CLASS
            case _: ScTypeDefinition => kinds contains CLASS
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

  def methodType(m : PsiMethod, s : ScSubstitutor) = new ScFunctionType(s.subst(ScType.create(m.getReturnType, m.getProject)),
                                                              Seq(m.getParameterList.getParameters.map {
                                                                p => val pt = p.getType
                                                                     //scala hack: Objects in java are modelled as Any in scala
                                                                     if (pt.equalsToText("java.lang.Object")) Any
                                                                     else s.subst(ScType.create(pt, m.getProject))
                                                              }: _*))

  def isAccessible(member: PsiMember, place: PsiElement): Boolean = {
    if (member.hasModifierProperty("public")) return true
    member match {
      case scMember: ScMember => scMember.getModifierList.accessModifier match {
        case None => true
        case Some(am: ScAccessModifier) => {
          if (am.isPrivate) {
            am.id match {
              case Some(id: PsiElement) => {
                id match {
                  case x if x.getNode.getElementType == ScalaTokenTypes.kTHIS => {
                    /*
                    ScalaRefernce.pdf:
                      A member M marked with this modifier can be accessed only from
                      within the object in which it is defined.
                    */
                    val enclosing = PsiTreeUtil.getContextOfType(scMember, classOf[ScTemplateDefinition], false)
                    if (enclosing == null) return true
                    PsiTreeUtil.isAncestor(enclosing, place, false)
                  }
                  case _ => {
                    val ref = am.getReference
                    val bind = ref.resolve
                    if (bind == null) return true
                    bind match {
                      case td: ScTemplateDefinition => {
                        PsiTreeUtil.isAncestor(td, place, false) || PsiTreeUtil.isAncestor(ScalaPsiUtil.getCompanionModule(td).getOrElse(null: PsiElement), place, false)
                      }
                      case pack: PsiPackage => {
                        val packageName = pack.getQualifiedName
                        val placeEnclosing: PsiElement = ScalaPsiUtil.getParentOfType(place, classOf[ScPackaging], classOf[ScalaFile])
                        if (placeEnclosing == null) return false //todo: not Scala, could be useful to implement
                        val placePackageName = placeEnclosing match {
                          case file: ScalaFile => file.getPackageName
                          case pack: ScPackaging => pack.getPackageName
                        }
                        return placePackageName.startsWith(packageName)
                      }
                      case _ => true
                    }
                  }
                }
              }
              case None => {
                /*
                ScalaRefernce.pdf:
                  Such members can be accessed only from within the directly enclosing
                  template and its companion module or companion class
                */
                val enclosing = ScalaPsiUtil.getParentOfType(scMember,
                  classOf[ScalaFile], classOf[ScPackaging], classOf[ScTemplateDefinition])
                enclosing match {
                  case td: ScTemplateDefinition => {
                    PsiTreeUtil.isAncestor(td, place, false) || PsiTreeUtil.isAncestor(ScalaPsiUtil.getCompanionModule(td).getOrElse(null: PsiElement), place, false)
                  }
                  case file: ScalaFile if file.isScriptFile => {
                    PsiTreeUtil.isAncestor(file, place, false)
                  }
                  case _ => {
                    val packageName = enclosing match {
                      case file: ScalaFile => file.getPackageName
                      case packaging: ScPackaging => packaging.getPackageName
                    }
                    val placeEnclosing: PsiElement = ScalaPsiUtil.getParentOfType(place, classOf[ScPackaging], classOf[ScalaFile])
                    if (placeEnclosing == null) return false //todo: not Scala, could be useful to implement
                    val placePackageName = placeEnclosing match {
                      case file: ScalaFile => file.getPackageName
                      case pack: ScPackaging => pack.getPackageName
                    }
                    return placePackageName.startsWith(packageName)
                  }
                }
              }
            }
          } else if (am.isProtected) {
            true
            //todo:
          } else true
        }
      }
      case _ => JavaPsiFacade.getInstance(place.getProject).
              getResolveHelper.isAccessible(member, place, null)
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

  def getLookupElement(resolveResult: ScalaResolveResult, qualifierType: ScType = Nothing): (LookupElement, PsiElement) = {
    import PresentationUtil.presentationString
    val element = resolveResult.element
    val substitutor = resolveResult.substitutor
    val isRenamed: Option[String] = resolveResult.isRenamed match {
      case Some(x) if element.getName != x => Some(x)
      case _ => None
    }

    val name: String = isRenamed.getOrElse(element.getName)
    val lookupBuilder: LookupElementBuilder = LookupElementFactory.builder(name, Tuple(element)) //don't add elements to lookup
    lookupBuilder.setInsertHandler(new ScalaInsertHandler)
    lookupBuilder.setRenderer(new LookupElementRenderer[LookupElement] {
      def renderElement(ignore: LookupElement, presentation: LookupElementPresentation): Unit = {
        var isBold = false
        var isDeprecated = false
        ScType.extractClassType(qualifierType) match {
          case Some((clazz, _)) =>  {
            element match {
              case m: PsiMember  => {
                if (m.getContainingClass == clazz) isBold = true
              }
              case _ =>
            }
          }
          case _ =>
        }
        element match {
          case doc: PsiDocCommentOwner if doc.isDeprecated => isDeprecated = true
          case _ =>
        }
        element match {
          //scala
          case fun: ScFunction => {
            presentation.setTypeText(presentationString(fun.returnType, substitutor))
            presentation.setTailText(presentationString(fun.paramClauses, substitutor))
          }
          case fun: ScFun => {
            presentation.setTypeText(presentationString(fun.retType, substitutor))
            presentation.setTailText(fun.paramTypes.map(presentationString(_, substitutor)).mkString("(", ", ", ")"))
          }
          case bind: ScBindingPattern => {
            presentation.setTypeText(presentationString(bind.calcType, substitutor))
          }
          case param: ScParameter => {
            presentation.setTypeText(presentationString(param.calcType, substitutor))
          }
          case clazz: PsiClass => {
            val location: String = clazz.getPresentation.getLocationString
            presentation.setTailText(" " + location, true)
          }
          case alias: ScTypeAliasDefinition => {
            presentation.setTypeText(presentationString(alias.aliasedType.resType, substitutor))
          }
          case method: PsiMethod => {
            presentation.setTypeText(presentationString(method.getReturnType, substitutor))
            presentation.setTailText(presentationString(method.getParameterList, substitutor))
          }
          case f: PsiField => {
            presentation.setTypeText(presentationString(f.getType, substitutor))
          }
          case _ =>
        }
        presentation.setIcon(element.getIcon(0))
        presentation.setItemText(name + (if (isRenamed == None) "" else " <= " + element.getName) + (element match {
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
          case p: PsiTypeParameterListOwner => "" //todo:
          case _ => ""
        }))
        presentation.setStrikeout(isDeprecated)
        presentation.setItemTextBold(isBold)
      }
    })
    (lookupBuilder.createLookupElement, element)
  }
}
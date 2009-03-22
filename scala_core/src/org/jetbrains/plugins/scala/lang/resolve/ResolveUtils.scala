package org.jetbrains.plugins.scala.lang.resolve
import com.intellij.psi.util.PsiTreeUtil
import psi.api.base.{ScAccessModifier, ScFieldId}
import psi.api.expr.ScSuperReference
import psi.api.ScalaFile
import psi.api.toplevel.typedef._
import psi.api.toplevel.ScModifierListOwner
import psi.impl.toplevel.typedef.TypeDefinitionMembers
import psi.types._
import _root_.scala.collection.Set
import psi.api.statements.{ScTypeAlias, ScFun, ScVariable}
import psi.api.statements.params.{ScParameter, ScTypeParam}
import com.intellij.psi._
import psi.api.base.patterns.ScBindingPattern
import psi.api.toplevel.packaging.ScPackaging
import psi.{ScalaPsiUtil, ScalaPsiElement}
import ResolveTargets._

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
            case _: PsiMethod => kinds contains METHOD
            case _: ScFun => kinds contains METHOD
            case f: PsiField => (kinds contains VAR) || (f.hasModifierProperty(PsiModifier.FINAL) && kinds.contains(VAL))
            case _ => false
          })

  def methodType(m : PsiMethod, s : ScSubstitutor) = new ScFunctionType(s.subst(ScType.create(m.getReturnType, m.getProject)),
                                                              m.getParameterList.getParameters.map {
                                                                p => val pt = p.getType
                                                                     //scala hack: Objects in java are modelled as Any in scala
                                                                     if (pt.equalsToText("java.lang.Object")) Any
                                                                     else s.subst(ScType.create(pt, m.getProject))
                                                              })

  def isAccessible(member: PsiMember, place: PsiElement): Boolean = member match {
    case scMember: ScMember => scMember.getModifierList.accessModifier match {
      case None => true
      case Some(am: ScAccessModifier) => {
        if (am.isPrivate) {
          am.id match {
            case Some(id: PsiElement) => true //todo:
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
                  val name = td.getName
                  val scope = td.getParent
                  var companion: PsiElement = null
                  td match {
                    case _: ScClass => {
                      companion = scope.getChildren.find((child: PsiElement) =>
                              child.isInstanceOf[ScObject] && child.asInstanceOf[ScObject].getName == name).getOrElse(null: PsiElement)
                    }
                    case _: ScObject => {
                      companion = scope.getChildren.find((child: PsiElement) =>
                              child.isInstanceOf[ScObject] && child.asInstanceOf[ScObject].getName == name).getOrElse(null: PsiElement)
                    }
                    case _ =>
                  }
                  if (PsiTreeUtil.isAncestor(td, place, false) || PsiTreeUtil.isAncestor(companion, place, false)) true
                  else false
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
        } else true //todo: protected
      }
    }
    case _ => JavaPsiFacade.getInstance(place.getProject).
            getResolveHelper.isAccessible(member, place, null) //todo: maybe it's not work
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
}
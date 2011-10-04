package org.jetbrains.plugins.scala
package overrideImplement

import com.intellij.codeInsight.generation.ClassMember
import com.intellij.openapi.editor.Editor

import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil

import com.intellij.ui.NonFocusableCheckBox
import com.intellij.util.IncorrectOperationException
import com.intellij.psi._
import lang.psi.api.toplevel.typedef.{ScTrait, ScTypeDefinition, ScMember, ScTemplateDefinition}
import lang.psi.api.toplevel.ScTypedDefinition
import lang.psi.api.statements._
import lang.psi.impl.ScalaPsiElementFactory
import lang.psi.types.{ScType, PhysicalSignature, ScSubstitutor}
import lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.util.ScalaUtils
import com.intellij.ide.util.MemberChooser
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.openapi.project.Project
import settings.ScalaApplicationSettings
import lang.psi.types.result.{Failure, Success, TypingContext}
import javax.swing.{JComponent, JCheckBox}
import collection.immutable.HashSet

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.07.2008
 */

object ScalaOIUtil {
  def toMembers(candidates: Seq[Any]): Array[ClassMember] = {
    val classMembersBuf = new ArrayBuffer[ClassMember]
    for (candidate <- candidates) {
      candidate match {
        case sign: PhysicalSignature => {
          assert(sign.method.getContainingClass != null, "Containing Class is null: " + sign.method.getText)
          classMembersBuf += new ScMethodMember(sign)
        }
        case (name: PsiNamedElement, subst: ScSubstitutor) => {
          ScalaPsiUtil.nameContext(name) match {
            case x: ScValue => {
              assert(x.getContainingClass != null, "Containing Class is null: " + x.getText)
              name match {
                case y: ScTypedDefinition => classMembersBuf += new ScValueMember(x, y, subst)
                case _ => throw new IncorrectOperationException("Not supported type:" + x)
              }
            }
            case x: ScVariable => {
              assert(x.getContainingClass != null, "Containing Class is null: " + x.getText)
              name match {
                case y: ScTypedDefinition => classMembersBuf += new ScVariableMember(x, y, subst)
                case _ => throw new IncorrectOperationException("Not supported type:" + x)
              }
            }
            case x: ScTypeAlias => {
              assert(x.getContainingClass != null, "Containing Class is null: " + x.getText)
              classMembersBuf += new ScAliasMember(x, subst)
            }
            case x => throw new IncorrectOperationException("Not supported type:" + x)
          }
        }
        case x => throw new IncorrectOperationException("Not supported type:" + x)
      }
    }
    classMembersBuf.toArray
  }

  def invokeOverrideImplement(project: Project, editor: Editor, file: PsiFile, isImplement: Boolean) {
    val elem = file.findElementAt(editor.getCaretModel.getOffset - 1)
    def getParentClass(elem: PsiElement): PsiElement = {
      elem match {
        case _: ScTemplateDefinition | null => elem
        case _ => getParentClass(elem.getParent)
      }
    }
    val parent = getParentClass(elem)
    if (parent == null) return
    val clazz = parent.asInstanceOf[ScTemplateDefinition]
    val candidates = if (isImplement) getMembersToImplement(clazz) else getMembersToOverride(clazz)
    if (candidates.isEmpty) return
    val classMembers = toMembers(candidates)
    val dontInferReturnTypeCheckBox: JCheckBox = new NonFocusableCheckBox(
      ScalaBundle.message("specify.return.type.explicitly"))
    if (ScalaApplicationSettings.getInstance.SPECIFY_RETURN_TYPE_EXPLICITLY != null)
      dontInferReturnTypeCheckBox.setSelected(ScalaApplicationSettings.getInstance.SPECIFY_RETURN_TYPE_EXPLICITLY.booleanValue)
    class ScalaMemberChooser extends MemberChooser[ClassMember](classMembers, false, true, project, Array(dontInferReturnTypeCheckBox)) {
      def needsInferType = dontInferReturnTypeCheckBox.isSelected
    }
    val chooser = new ScalaMemberChooser
    chooser.setTitle(if (isImplement) ScalaBundle.message("select.method.implement")
                     else ScalaBundle.message("select.method.override"))
    chooser.show()

    val selectedMembers = chooser.getSelectedElements
    if (selectedMembers == null || selectedMembers.size == 0) return
    val needsInferType = chooser.needsInferType
    ScalaApplicationSettings.getInstance.SPECIFY_RETURN_TYPE_EXPLICITLY = needsInferType
    runAction(selectedMembers, isImplement, clazz, editor, needsInferType)
  }

  def runAction(selectedMembers: java.util.List[ClassMember],
               isImplement: Boolean, clazz: ScTemplateDefinition, editor: Editor, needsInferType: Boolean) {
    ScalaUtils.runWriteAction(new Runnable {
      def run() {
        def addUpdateThisType(subst: ScSubstitutor) = clazz.getType(TypingContext.empty) match {
          case Success(tpe, _) => subst.addUpdateThisType(tpe)
          case Failure(_, _) => subst
        }

        for (member <- selectedMembers.toArray(new Array[ClassMember](selectedMembers.size)).reverse) {
          val offset = editor.getCaretModel.getOffset
          val anchor = getAnchor(offset, clazz)
          member match {
            case member: ScMethodMember => {
              val method: PsiMethod = member.getElement
              val sign = member.sign.updateSubst(addUpdateThisType)
              val m = ScalaPsiElementFactory.createOverrideImplementMethod(sign, method.getManager, !isImplement, needsInferType)
              adjustTypesAndSetCaret(clazz.addMember(m, anchor), editor)
            }
            case member: ScAliasMember => {
              val alias = member.getElement
              val substitutor = addUpdateThisType(member.substitutor)
              val m = ScalaPsiElementFactory.createOverrideImplementType(alias, substitutor, alias.getManager, !isImplement)
              adjustTypesAndSetCaret(clazz.addMember(m, anchor), editor)
            }
            case _: ScValueMember | _: ScVariableMember => {
              val isVal = member match {case _: ScValueMember => true case _: ScVariableMember => false}
              val value = member match {case x: ScValueMember => x.element case x: ScVariableMember => x.element}
              val origSubstitutor = member match {
                case x: ScValueMember => x.substitutor
                case x: ScVariableMember => x.substitutor
              }
              val substitutor = addUpdateThisType(origSubstitutor)
              val m = ScalaPsiElementFactory.createOverrideImplementVariable(value, substitutor, value.getManager,
                !isImplement, isVal, needsInferType)
              adjustTypesAndSetCaret(clazz.addMember(m, anchor), editor)
            }
            case _ =>
          }
        }
      }
    }, clazz.getProject, if (isImplement) "Implement method" else "Override method")
  }

  def getMembersToImplement(clazz: ScTemplateDefinition, withOwn: Boolean = false): Seq[ScalaObject] = {
    val buf = new ArrayBuffer[ScalaObject]
    buf ++= clazz.allSignatures
    buf ++= clazz.allTypeAliases
    buf ++= clazz.allVals
    val buf2 = new ArrayBuffer[ScalaObject]
    for (element <- buf) {
      element match {
        case sign: PhysicalSignature => {
          val m = sign.method
          val name = if (m == null) "" else m.getName
          m match {
            case _ if isProductAbstractMethod(m, clazz) =>
            case x if name == "$tag" || name == "$init$" =>
            case x if !withOwn && x.getContainingClass == clazz =>
            case x if x.getContainingClass.isInterface && !x.getContainingClass.isInstanceOf[ScTrait] => {
              buf2 += sign
            }
            case x if x.hasModifierProperty("abstract") => {
              buf2 += sign
            }
            case x: ScFunctionDeclaration => {
              buf2 += sign
            }
            case _ =>
          }
        }
        case (name: PsiNamedElement, subst: ScSubstitutor) => {
          ScalaPsiUtil.nameContext(name) match {
            case x: ScValueDeclaration if withOwn || x.getContainingClass != clazz => buf2 += element
            case x: ScVariableDeclaration if withOwn || x.getContainingClass != clazz => buf2 += element
            case x: ScTypeAliasDeclaration if withOwn || x.getContainingClass != clazz => buf2 += element
            case _ =>
          }
        }
        case _ =>
      }
    }
    buf2.toSeq
  }

  def isProductAbstractMethod(m: PsiMethod, clazz: PsiClass,
                              visited: HashSet[PsiClass] = new HashSet) : Boolean = {
    if (visited.contains(clazz)) return false
    clazz match {
      case td: ScTypeDefinition if td.isCase => {
        if (m.getName == "apply") return true
        if (m.getName == "canEqual") return true
        val clazz = m.getContainingClass
        clazz != null && clazz.getQualifiedName == "scala.Product" &&
          (m.getName match {
            case "productArity" | "productElement" => true
            case _ => false
          })
      }
      case x: ScTemplateDefinition => (x.superTypes.map(t => ScType.extractClass(t)).find {
        case Some(c) => isProductAbstractMethod(m, c, visited + clazz)
        case _ => false
      }) match {
        case Some(_) => true
        case _ => false
      }
      case _ => false
    }
  }

  def getMembersToOverride(clazz: ScTemplateDefinition): Seq[ScalaObject] = {
    val buf = new ArrayBuffer[ScalaObject]
    buf ++= clazz.allMethods
    buf ++= clazz.allTypeAliases
    buf ++= clazz.allVals
    val buf2 = new ArrayBuffer[ScalaObject]
    for (element <- buf) {
      element match {
        case sign: PhysicalSignature => {
          sign.method match {
            case _ if isProductAbstractMethod(sign.method, clazz) => buf2 += sign
            case _: ScFunctionDeclaration =>
            case x if x.getName == "$tag" || x.getName == "$init$"=>
            case x: ScFunction if x.isSyntheticCopy =>
            case x if x.getContainingClass == clazz =>
            case x: PsiModifierListOwner if x.hasModifierProperty("abstract")
                || x.hasModifierProperty("final") /*|| x.hasModifierProperty("sealed")*/ =>
            case x if x.isConstructor =>
            case method => {
              var flag = false
              if (method match {case x: ScFunction => x.parameters.length == 0 case _ => method.getParameterList.getParametersCount == 0}) {
                for (pair <- clazz.allVals; v = pair._1) if (v.getName == method.getName) {
                  ScalaPsiUtil.nameContext(v) match {
                    case x: ScValue if x.getContainingClass == clazz => flag = true
                    case x: ScVariable if x.getContainingClass == clazz => flag = true
                    case _ =>
                  }
                }
              }
              if (!flag) buf2 += sign
            }
          }
        }
        case (name: PsiNamedElement, subst: ScSubstitutor) => {
          ScalaPsiUtil.nameContext(name) match {
            case x: PsiModifierListOwner if x.hasModifierProperty("final") =>
            case x: ScPatternDefinition if x.getContainingClass != clazz => {
              var flag = false
              for (signe <- clazz.allMethods if signe.method.getContainingClass == clazz) {
                //getContainingClass == clazz so we sure that this is ScFunction (it is safe cast)
                signe.method match {
                  case fun: ScFunction => if (fun.parameters.length == 0 && fun.getName == x.getName) flag = true
                  case _ =>  //todo: ScPrimaryConstructor?
                }
              }
              for (pair <- clazz.allVals; v = pair._1) if (v.getName == name.getName) {
                ScalaPsiUtil.nameContext(v) match {
                  case x: ScValue if x.getContainingClass == clazz => flag = true
                  case x: ScVariable if x.getContainingClass == clazz => flag = true
                  case _ =>
                }
              }
              if (!flag) buf2 += element
            }
            case x: ScVariableDefinition if x.getContainingClass != clazz => {
              var flag = false
              for (signe <- clazz.allMethods if signe.method.getContainingClass == clazz) {
                //getContainingClass == clazz so we sure that this is ScFunction (it is safe cast)
                if (signe.method.isInstanceOf[ScFunction] &&
                        signe.method.asInstanceOf[ScFunction].parameters.length == 0 &&
                        signe.method.getName == x.getName) flag = true
              }
              for (pair <- clazz.allVals; v = pair._1) if (v.getName == name.getName) {
                ScalaPsiUtil.nameContext(v) match {
                  case x: ScValue if x.getContainingClass == clazz => flag = true
                  case x: ScVariable if x.getContainingClass == clazz => flag = true
                  case _ =>
                }
              }
              if (!flag) buf2 += element
            }
            case x: ScTypeAliasDefinition if x.getContainingClass != clazz => buf2 += element
            case _ =>
          }
        }
        case _ =>
      }
    }

    buf2.toSeq
  }



  def getMethod(clazz: ScTypeDefinition, methodName: String, isImplement: Boolean): ScMember = {
    val seq: Seq[ScalaObject] = if (isImplement) getMembersToImplement(clazz) else getMembersToOverride(clazz)
    def getObjectByName: ScalaObject = {
      for (obj <- seq) {
        obj match {
          case sign: PhysicalSignature if sign.method.getName == methodName => return sign
          case obj@(name: PsiNamedElement, subst: ScSubstitutor) if name.getName == methodName => return obj
          case _ =>
        }
      }
      null
    }
    val obj = getObjectByName
    if (obj == null) return null

    def addUpdateThisType(subst: ScSubstitutor) = clazz.getType(TypingContext.empty) match {
      case Success(tpe, _) => subst.addUpdateThisType(tpe)
      case Failure(_, _) => subst
    }

    obj match {
      case sign: PhysicalSignature => {
        val method: PsiMethod = sign.method
        val sign1 = sign.updateSubst(addUpdateThisType)
        ScalaPsiElementFactory.createOverrideImplementMethod(sign1, method.getManager, !isImplement, true)
      }
      case (name: PsiNamedElement, subst: ScSubstitutor) => {
        val element: PsiElement = ScalaPsiUtil.nameContext(name)
        element match {
          case alias: ScTypeAlias => {
            val subst1 = addUpdateThisType(subst)
            ScalaPsiElementFactory.createOverrideImplementType(alias, subst1, alias.getManager, !isImplement)
          }
          case _: ScValue | _: ScVariable => {
            val typed: ScTypedDefinition = name match {case x: ScTypedDefinition => x case _ => return null}
            val subst1 = addUpdateThisType(subst)
            ScalaPsiElementFactory.createOverrideImplementVariable(typed, subst1, typed.getManager, !isImplement,
              element match {case _: ScValue => true case _ => false}, true)
          }
          case _ => null
        }
      }
    }
  }

  def getAnchor(offset: Int, clazz: ScTemplateDefinition) : Option[ScMember] = {
    val body = clazz.extendsBlock.templateBody match {
      case Some(x) => x
      case None => return None
    }
    var element: PsiElement = body.getContainingFile.findElementAt(offset)
    while (element != null && element.getParent != body) element = element.getParent

    element match {
      case member: ScMember => Some(member)
      case null => None
      case _ => PsiTreeUtil.getNextSiblingOfType(element, classOf[ScMember]) match {
        case null => None
        case member => Some(member)
      }
    }
  }

  private def adjustTypesAndSetCaret(meth: PsiElement, editor: Editor) {
    ScalaPsiUtil.adjustTypes(meth)
    //hack for postformatting IDEA bug.
    val member = CodeStyleManager.getInstance(meth.getProject).reformat(meth)
    //Setting selection
    val body: PsiElement = member match {
      case meth: ScTypeAliasDefinition => meth.aliasedTypeElement
      case meth: ScPatternDefinition => meth.expr
      case meth: ScVariableDefinition => meth.expr
      case method: ScFunctionDefinition => method.body match {
        case Some(x) => x
        case None => return
      }
      case _ => return
    }
    val range = body.getTextRange
    val offset = range.getStartOffset
    if (body.getText == "{}") {
      editor.getCaretModel.moveToOffset(offset + 1)
    } else {
      editor.getCaretModel.moveToOffset(offset)
      editor.getSelectionModel.setSelection(range.getStartOffset, range.getEndOffset)
    }
  }
}
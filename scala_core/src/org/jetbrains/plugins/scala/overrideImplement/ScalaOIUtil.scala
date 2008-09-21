package org.jetbrains.plugins.scala.overrideImplement

import com.intellij.codeInsight.generation.{PsiMethodMember, OverrideImplementUtil, ClassMember, PsiFieldMember}
import com.intellij.openapi.editor.{Editor, VisualPosition}
import com.intellij.psi.tree.IElementType
import lang.lexer.ScalaTokenTypes
import lang.psi.api.toplevel.ScModifierListOwner
import com.intellij.psi._
import lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement, ScFieldId}
import annotations.Nullable
import lang.psi.api.base.patterns.ScReferencePattern
import lang.psi.api.toplevel.templates.ScTemplateBody
import lang.psi.types.{ScType, PhysicalSignature, ScSubstitutor}
import lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import lang.psi.ScalaPsiElement
import lang.psi.api.base.types.ScSimpleTypeElement
import lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import lang.psi.api.statements._
import com.intellij.ide.highlighter.JavaFileType
import lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.ScalaUtils
import com.intellij.util.IncorrectOperationException
import com.intellij.ide.util.MemberChooser
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.psi.infos.CandidateInfo
import lang.psi.api.toplevel.typedef.ScTypeDefinition
import com.intellij.openapi.project.Project
/**
 * User: Alexander Podkhalyuzin
 * Date: 08.07.2008
 */

object ScalaOIUtil {
  def invokeOverrideImplement(project: Project, editor: Editor, file: PsiFile, isImplement: Boolean) {
    val elem = file.findElementAt(editor.getCaretModel.getOffset)
    def getParentClass(elem: PsiElement): PsiElement = {
      elem match {
        case _: ScTypeDefinition | null => return elem
        case _ => getParentClass(elem.getParent)
      }
    }
    val parent = getParentClass(elem)
    if (parent == null) return
    val clazz = parent.asInstanceOf[ScTypeDefinition]
    //val candidates = if (isImplement) ScalaOIUtil.getMembersToImplement(clazz) else ScalaOIUtil.getMembersToOverride(clazz)
    val candidates = if (isImplement) getMembersToImplement(clazz) else getMembersToOverride(clazz)
    if (candidates.isEmpty) return
    val classMembersBuf = new ArrayBuffer[ClassMember]
    for (candidate <- candidates) yield {
      candidate match {
        case sign: PhysicalSignature => classMembersBuf += new ScMethodMember(sign)
      /*case x: PsiMethod => classMembersBuf += new PsiMethodMember(x)
        case x: ScTypeAlias => classMembersBuf += new ScAliasMember(x)
        case x: ScReferencePattern => {
          valvarContext(x) match {
            case y: ScValue => classMembersBuf += new ScValueMember(y, x)
            case y: ScVariable => classMembersBuf += new ScVariableMember(y, x)
            case _ => {
              throw new IncorrectOperationException
              null
            }
          }
        }
        case x: ScFieldId => {
          valvarContext(x) match {
            case y: ScValue => classMembersBuf += new ScValueMember(y, x)
            case y: ScVariable => classMembersBuf += new ScVariableMember(y, x)
            case _ => {
              throw new IncorrectOperationException
              null
            }
          }
        }
        case x: PsiField => //todo: dont add now: classMembersBuf += new PsiFieldMember(x)*/
        case x => {
          throw new IncorrectOperationException("Not supported type:" + x)
          null
        }
      }
    }
    val classMembers = classMembersBuf.toArray
    val chooser = new MemberChooser[ClassMember](classMembers, false, true, project)
    chooser.setTitle(if (isImplement) ScalaBundle.message("select.method.implement", Array[Object]())
                     else ScalaBundle.message("select.method.override", Array[Object]()))
    chooser.show

    val selectedMembers = chooser.getSelectedElements
    if (selectedMembers == null || selectedMembers.size == 0) return
    for (member <- selectedMembers.toArray(new Array[ClassMember](selectedMembers.size))) {
      //todo: create body from template
      member match {
        case member: ScMethodMember => {
          val method: PsiMethod = member.getElement
          val sign = member.sign
          ScalaUtils.runWriteAction(new Runnable {
            def run {
              var meth = ScalaPsiElementFactory.createOverrideImplementMethod(sign, method.getManager, !isImplement)
              val body: ScTemplateBody = clazz.extendsBlock.templateBody match {
                case Some(x) => x
                case None => return
              }
              //if body is not empty
              if (body.getChildren.length != 0) {
                val offset = editor.getCaretModel.getOffset
                //current element
                var element = body.getContainingFile.findElementAt(offset)
                while (element != null && element.getParent != body) element = element.getParent
                if (element == null) return
                //Look at some exceptions
                val t = element.getNode.getElementType
                element.getNode.getElementType match {
                  case ScalaTokenTypes.tLINE_TERMINATOR | TokenType.WHITE_SPACE => element = element.getNextSibling
                  case ScalaTokenTypes.tLBRACE => {
                    element = element.getNextSibling
                    element.getNode.getElementType match {
                      case ScalaTokenTypes.tLINE_TERMINATOR => element = element.getNextSibling
                      case _ => body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(meth.getManager), element.getNode)
                    }
                  }
                  case _ =>
                }
                //now we can add new statement before this element or after if it is the end
                body.getNode.addChild(meth.getNode, element.getNode)
                body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(meth.getManager), element.getNode)
              } else {
                val newBody: ScTemplateBody = body.replace(ScalaPsiElementFactory.createOverrideImplementMethodBody(sign, method.getManager, !isImplement)).asInstanceOf[ScTemplateBody]
                meth = newBody.functions(0)
                newBody.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(meth.getManager), meth.getNode)
              }
              meth match {
                case method: ScFunctionDefinition => {
                  val body = method.body match {
                    case Some(x) => x
                    case None => return
                  }
                  val offset = body.getTextRange.getStartOffset
                  editor.getCaretModel.moveToOffset(offset)
                  editor.getSelectionModel.setSelection(body.getTextRange.getStartOffset, body.getTextRange.getEndOffset)
                }
                case _ =>
              }
            }
          }, method.getProject, if (isImplement) "Implement method" else "Override method")
        }
        case member: ScAliasMember => {
          val alias = member.getElement
          ScalaUtils.runWriteAction(new Runnable {
            def run {
              var meth = ScalaPsiElementFactory.createOverrideImplementType(alias, alias.getManager, !isImplement)
              val body = clazz.extendsBlock.templateBody match {
                case Some(x) => x
                case None => return
              }
              //if body is not empty
              if (body.getChildren.length != 0) {
                val offset = editor.getCaretModel.getOffset
                //current element
                var element = body.getContainingFile.findElementAt(offset)
                while (element != null && element.getParent != body) element = element.getParent
                if (element == null) return
                //Look at some exceptions
                val t = element.getNode.getElementType
                element.getNode.getElementType match {
                  case ScalaTokenTypes.tLINE_TERMINATOR | TokenType.WHITE_SPACE => element = element.getNextSibling
                  case ScalaTokenTypes.tLBRACE => {
                    element = element.getNextSibling
                    element.getNode.getElementType match {
                      case ScalaTokenTypes.tLINE_TERMINATOR => element = element.getNextSibling
                      case _ => body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(meth.getManager), element.getNode)
                    }
                  }
                  case _ =>
                }
                //now we can add new statement before this element or after if it is the end
                body.getNode.addChild(meth.getNode, element.getNode)
                body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(meth.getManager), element.getNode)
              } else {
                val newBody: ScTemplateBody = body.replace(ScalaPsiElementFactory.createOverrideImplementTypeBody(alias, alias.getManager, !isImplement)).asInstanceOf[ScTemplateBody]
                meth = newBody.aliases(0)
                newBody.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(meth.getManager), meth.getNode)
              }
              meth match {
                case meth: ScTypeAliasDefinition => {
                  val body = meth.aliasedTypeElement
                  val offset = body.getTextRange.getStartOffset
                  editor.getCaretModel.moveToOffset(offset)
                  editor.getSelectionModel.setSelection(body.getTextRange.getStartOffset, body.getTextRange.getEndOffset)
                }
                case _ =>
              }
            }
          }, alias.getProject, if (isImplement) "Implement type alias" else "Override type alias")
        }
        case _: ScValueMember | _: ScVariableMember=> {
          val isVal = member match {case _: ScValueMember => true case _: ScVariableMember => false}
          val value = member match {case x: ScValueMember => x.element case x: ScVariableMember => x.element}
          ScalaUtils.runWriteAction(new Runnable {
            def run {
              var meth = ScalaPsiElementFactory.createOverrideImplementVariable(value, value.getManager, !isImplement, isVal)
              val body = clazz.extendsBlock.templateBody match {
                case Some(x) => x
                case None => return
              }
              //if body is not empty
              if (body.getChildren.length != 0) {
                val offset = editor.getCaretModel.getOffset
                //current element
                var element = body.getContainingFile.findElementAt(offset)
                while (element != null && element.getParent != body) element = element.getParent
                if (element == null) return
                //Look at some exceptions
                val t = element.getNode.getElementType
                element.getNode.getElementType match {
                  case ScalaTokenTypes.tLINE_TERMINATOR | TokenType.WHITE_SPACE => element = element.getNextSibling
                  case ScalaTokenTypes.tLBRACE => {
                    element = element.getNextSibling
                    element.getNode.getElementType match {
                      case ScalaTokenTypes.tLINE_TERMINATOR => element = element.getNextSibling
                      case _ => body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(meth.getManager), element.getNode)
                    }
                  }
                  case _ =>
                }
                //now we can add new statement before this element or after if it is the end
                body.getNode.addChild(meth.getNode, element.getNode)
                body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(meth.getManager), element.getNode)
              } else {
                val newBody: ScTemplateBody = body.replace(ScalaPsiElementFactory.createOverrideImplementVariableBody(value, value.getManager, !isImplement, isVal)).asInstanceOf[ScTemplateBody]
                meth = newBody.members(0)
                newBody.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(meth.getManager), meth.getNode)
              }
              meth match {
                case meth: ScPatternDefinition => {
                  val body = meth.expr
                  val offset = body.getTextRange.getStartOffset
                  editor.getCaretModel.moveToOffset(offset)
                  editor.getSelectionModel.setSelection(body.getTextRange.getStartOffset, body.getTextRange.getEndOffset)
                }
                case meth: ScVariableDefinition => {
                  val body = meth.expr
                  val offset = body.getTextRange.getStartOffset
                  editor.getCaretModel.moveToOffset(offset)
                  editor.getSelectionModel.setSelection(body.getTextRange.getStartOffset, body.getTextRange.getEndOffset)
                }
                case _ =>
              }
            }
          }, value.getProject, if (isImplement) "Implement value" else "Override value")
        }
        case _ =>
      }
    }
  }

  def getMembersToImplement(clazz: ScTypeDefinition): Seq[ScalaObject] = {
    val buf = new ArrayBuffer[ScalaObject]
    buf ++= clazz.allMethods
    buf ++= clazz.allTypes
    buf ++= clazz.allVals
    val buf2 = new ArrayBuffer[ScalaObject]
    for (element <- buf) {
      def addMethod(x: PhysicalSignature) {
        var flag = false
        for (obj <- buf) {
          obj match {
            case sign: PhysicalSignature => {
              sign.method match {
                case _: ScFunctionDeclaration =>
                case x if x.getName == "$tag" =>
                case x if x.getModifierList.hasModifierProperty("abstract") =>
                case x if x.isConstructor =>
                case _ => if (sign.equiv(x)) flag = true
              }
            }
            case _ =>
          }
        }
        if (!flag) buf2 += element
      }
      element match {
        case sign: PhysicalSignature => {
          sign.method match {
            case x if x.getName == "$tag" =>
            case x if x.getContainingClass.isInterface => addMethod(sign)
            case x if x.hasModifierProperty("abstract") => addMethod(sign)
            case x: ScFunctionDeclaration => addMethod(sign)
            case _ =>
          }
        }
        case (name: PsiNamedElement, subst: ScSubstitutor) => {
          nameContext(name) match {
            case x: ScValueDeclaration if x.getContainingClass != clazz => buf2 += element
            case x: ScVariableDeclaration if x.getContainingClass != clazz => buf2 += element
            case x: ScTypeAliasDeclaration => buf2 += element
            case _ =>
          }
        }
        case _ =>
      }
    }
    return buf2.toSeq
  }

  def getMembersToOverride(clazz: ScTypeDefinition): Seq[ScalaObject] = {
    val buf = new ArrayBuffer[ScalaObject]
    buf ++= clazz.allMethods
    buf ++= clazz.allTypes
    buf ++= clazz.allVals
    val buf2 = new ArrayBuffer[ScalaObject]
    for (element <- buf) {
      element match {
        case sign: PhysicalSignature => {
          sign.method match {
            case _: ScFunctionDeclaration =>
            case x if x.getName == "$tag" =>
            case x if x.getContainingClass == clazz =>
            case x: PsiModifierListOwner if x.hasModifierProperty("abstract")
              || x.hasModifierProperty("final") =>
            case x if x.isConstructor =>
            case method => {
              var flag = false
              for (signe <- clazz.allMethods if signe.method.getContainingClass == clazz) {
                if (sign.equiv(signe)) flag = true
              }
              if (method match {case x: ScFunction => x.parameters.length == 0 case _ => method.getParameterList.getParametersCount == 0}) {
                for (pair <- clazz.allVals; v = pair._1) if (v.getName == method.getName) {
                  nameContext(v) match {
                    case x: ScValue if x.getContainingClass == clazz => flag = true
                    case x: ScVariable if x.getContainingClass == clazz => flag = true
                    case _ =>
                  }
                }
              }
              if (!flag) buf2 += element
            }
          }
        }
        case (name: PsiNamedElement, subst: ScSubstitutor) => {
          nameContext(name) match {
            case x: ScPatternDefinition if x.getContainingClass != clazz => {
              var flag = false
              for (signe <- clazz.allMethods if signe.method.getContainingClass == clazz) {
                //getContainingClass == clazz so we sure that this is ScFunction (it is safe cast)
                if (signe.method.asInstanceOf[ScFunction].parameters.length == 0 && signe.method.getName == x.getName) flag = true
              }
              for (pair <- clazz.allVals; v = pair._1) if (v.getName == name.getName) {
                nameContext(v) match {
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
                if (signe.method.asInstanceOf[ScFunction].parameters.length == 0 && signe.method.getName == x.getName) flag = true
              }
              for (pair <- clazz.allVals; v = pair._1) if (v.getName == name.getName) {
                nameContext(v) match {
                  case x: ScValue if x.getContainingClass == clazz => flag = true
                  case x: ScVariable if x.getContainingClass == clazz => flag = true
                  case _ =>
                }
              }
              if (!flag) buf2 += element
            }
            case x: ScTypeAliasDefinition if x.getContainingClass != clazz => {
              var flag = false
              for (pair <- clazz.allVals; v = pair._1) if (v.getName == name.getName) {
                nameContext(v) match {
                  case x: ScTypeAlias if x.getContainingClass == clazz => flag = true
                  case _ =>
                }
              }
              if (!flag) buf2 += element
            }
          }
        }
        case _ =>
      }
    }
    return buf2.toArray
  }

  private def nameContext(x: PsiNamedElement): PsiElement = {
    var parent = x.getParent
    def test(x: PsiElement): Boolean = {
      x match {
        case _: ScValue | _: ScVariable | _: ScTypeAlias => true
        case _ => false
      }
    }
    while (parent != null && !test(parent)) parent = parent.getParent
    return parent
  }

  @Nullable
  private def valvarContext(x: PsiElement): PsiElement = {
    var parent = x.getParent
    while (parent != null && !parent.isInstanceOf[ScValue] && !parent.isInstanceOf[ScVariable]) parent = parent.getParent
    return parent
  }
}
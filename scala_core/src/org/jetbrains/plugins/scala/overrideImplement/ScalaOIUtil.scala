package org.jetbrains.plugins.scala.overrideImplement

import com.intellij.codeInsight.generation.{PsiMethodMember, OverrideImplementUtil, ClassMember, PsiFieldMember}
import com.intellij.openapi.editor.{Editor, VisualPosition}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.tree.IElementType
import lang.lexer.ScalaTokenTypes
import com.intellij.psi._
import lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement, ScFieldId}
import annotations.Nullable
import lang.psi.api.base.patterns.ScReferencePattern
import lang.psi.api.toplevel.templates.ScTemplateBody
import lang.psi.api.toplevel.{ScModifierListOwner, ScTyped}
import lang.psi.types.{ScType, PhysicalSignature, ScSubstitutor}
import lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import lang.psi.api.base.types.ScSimpleTypeElement
import lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import lang.psi.api.statements._
import com.intellij.ide.highlighter.JavaFileType
import lang.psi.impl.ScalaPsiElementFactory
import lang.psi.{ScalaPsiUtil, ScalaPsiElement}
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
    for (candidate <- candidates) {
      candidate match {
        case sign: PhysicalSignature => classMembersBuf += new ScMethodMember(sign)
        case (name: PsiNamedElement, subst: ScSubstitutor) => {
          ScalaPsiUtil.nameContext(name) match {
            case x: ScValue => {
              name match {
                case y: ScTyped => classMembersBuf += new ScValueMember(x, y, subst)
                case _ => throw new IncorrectOperationException("Not supported type:" + x)
              }
            }
            case x: ScVariable => {
              name match {
                case y: ScTyped => classMembersBuf += new ScVariableMember(x, y, subst)
                case _ => throw new IncorrectOperationException("Not supported type:" + x)
              }
            }
            case x: ScTypeAlias => classMembersBuf += new ScAliasMember(x, subst)
            case x => throw new IncorrectOperationException("Not supported type:" + x)
          }
        }
        case x => throw new IncorrectOperationException("Not supported type:" + x)
      }
    }
    val classMembers = classMembersBuf.toArray
    val chooser = new MemberChooser[ClassMember](classMembers, false, true, project)
    chooser.setTitle(if (isImplement) ScalaBundle.message("select.method.implement", Array[Object]())
                     else ScalaBundle.message("select.method.override", Array[Object]()))
    chooser.setCopyJavadocVisible(false)
    chooser.show

    val selectedMembers = chooser.getSelectedElements
    if (selectedMembers == null || selectedMembers.size == 0) return
    runAction(selectedMembers, isImplement, clazz, editor)
  }

  def runAction(selectedMembers: java.util.List[ClassMember],
               isImplement: Boolean, clazz: ScTypeDefinition, editor: Editor) {
    ScalaUtils.runWriteAction(new Runnable {
      def run {
        for (member <- selectedMembers.toArray(new Array[ClassMember](selectedMembers.size))) {
          var meth: PsiElement = null
          val offset = editor.getCaretModel.getOffset
          val (anchor, pos) = getAnchorAndPos(offset, clazz)
          member match {
            case member: ScMethodMember => {
              val method: PsiMethod = member.getElement
              val sign = member.sign

              val m = ScalaPsiElementFactory.createOverrideImplementMethod(sign, method.getManager, !isImplement)
              meth = clazz.addMember(m, anchor, pos) match {case Some(x) => x case None => null}
              adjustTypesAndSetCaret(meth, editor)

            }
            case member: ScAliasMember => {
              val alias = member.getElement
              val substitutor = member.substitutor
              val m = ScalaPsiElementFactory.createOverrideImplementType(alias, substitutor, alias.getManager, !isImplement)
              meth = clazz.addMember(m, anchor, pos) match {case Some(x) => x case None => null}
              adjustTypesAndSetCaret(meth, editor)
            }
            case _: ScValueMember | _: ScVariableMember => {
              val isVal = member match {case _: ScValueMember => true case _: ScVariableMember => false}
              val value = member match {case x: ScValueMember => x.element case x: ScVariableMember => x.element}
              val substitutor = member match {case x: ScValueMember => x.substitutor case x: ScVariableMember => x.substitutor}
              val m = ScalaPsiElementFactory.createOverrideImplementVariable(value, substitutor, value.getManager, !isImplement, isVal)
              meth = clazz.addMember(m, anchor, pos) match {case Some(x) => x case None => null}
              adjustTypesAndSetCaret(meth, editor)
            }
            case _ =>
          }
        }
      }
    }, clazz.getProject, if (isImplement) "Implement method" else "Override method")
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
        if (x.method match {case x: ScFunction => x.parameters.length == 0 case method => method.getParameterList.getParametersCount == 0}) {
          for (obj <- buf) {
            obj match {
              case (name: PsiNamedElement, subst: ScSubstitutor) if name.getName == x.method.getName => {
                ScalaPsiUtil.nameContext(name) match {
                  case _: ScPatternDefinition | _: ScVariableDefinition => flag = true
                  case _ =>
                }
              }
              case _ =>
            }
          }
        }
        //todo: this wrong: cheking for Object methods:
        val objectType: PsiClass = JavaPsiFacade.getInstance(clazz.getProject).
            findClass("java.lang.Object", GlobalSearchScope.allScope(clazz.getProject))
        for (meth <- objectType.getAllMethods) {
          if (x.equiv(new PhysicalSignature(meth, ScSubstitutor.empty))) flag = true
        }
        if (!flag) buf2 += element
      }
      element match {
        case sign: PhysicalSignature => {
          sign.method match {
            case x if x.getName == "$tag" =>
            case x if x.getContainingClass == clazz =>
            case x if x.getContainingClass.isInterface => addMethod(sign)
            case x if x.hasModifierProperty("abstract") => addMethod(sign)
            case x: ScFunctionDeclaration => addMethod(sign)
            case _ =>
          }
        }
        case (name: PsiNamedElement, subst: ScSubstitutor) => {
          ScalaPsiUtil.nameContext(name) match {
            case x: ScValueDeclaration if x.getContainingClass != clazz => buf2 += element
            case x: ScVariableDeclaration if x.getContainingClass != clazz => buf2 += element
            case x: ScTypeAliasDeclaration if x.getContainingClass != clazz => buf2 += element
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
                || x.hasModifierProperty("final") || x.hasModifierProperty("sealed") =>
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
              if (!flag) buf2 += element
            }
          }
        }
        case (name: PsiNamedElement, subst: ScSubstitutor) => {
          ScalaPsiUtil.nameContext(name) match {
            case x: ScPatternDefinition if x.getContainingClass != clazz => {
              var flag = false
              for (signe <- clazz.allMethods if signe.method.getContainingClass == clazz) {
                //getContainingClass == clazz so we sure that this is ScFunction (it is safe cast)
                if (signe.method.asInstanceOf[ScFunction].parameters.length == 0 && signe.method.getName == x.getName) flag = true
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
                if (signe.method.asInstanceOf[ScFunction].parameters.length == 0 && signe.method.getName == x.getName) flag = true
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
    val objectType: PsiClass = JavaPsiFacade.getInstance(clazz.getProject).
        findClass("java.lang.Object", GlobalSearchScope.allScope(clazz.getProject))
    for (meth <- objectType.getAllMethods if !meth.isConstructor && !meth.hasModifierProperty("final")) {
      var flag = false
      val signature: PhysicalSignature = new PhysicalSignature(meth, ScSubstitutor.empty)
      for (signe <- clazz.allMethods if signe.method.getContainingClass == clazz) {
        if (signe.equiv(signature)) flag = true
      }
      for (signe <- buf2 if signe.isInstanceOf[PhysicalSignature]; sign = signe.asInstanceOf[PhysicalSignature]) {
        if (sign.equiv(signature)) flag = true
      }
      if (!flag) buf2 += signature
    }
    return buf2.toArray
  }



  /*
   This method used for test class OverrideImplementTest
   */
  def getMethod(clazz: ScTypeDefinition, methodName: String, isImplement: Boolean): PsiElement = {
    val seq: Seq[ScalaObject] = if (isImplement) getMembersToImplement(clazz) else getMembersToOverride(clazz)
    def getObjectByName: ScalaObject = {
      for (obj <- seq) {
        obj match {
          case sign: PhysicalSignature if sign.method.getName == methodName => return sign
          case obj@(name: PsiNamedElement, subst: ScSubstitutor) if name.getName == methodName => return obj
          case _ =>
        }
      }
      return null
    }
    val obj = getObjectByName
    if (obj == null) return null
    obj match {
      case sign: PhysicalSignature => {
        val method: PsiMethod = sign.method
        return ScalaPsiElementFactory.createOverrideImplementMethod(sign, method.getManager, !isImplement)
      }
      case (name: PsiNamedElement, subst: ScSubstitutor) => {
        val element: PsiElement = ScalaPsiUtil.nameContext(name)
        element match {
          case alias: ScTypeAlias => {
            return ScalaPsiElementFactory.createOverrideImplementType(alias, subst, alias.getManager, !isImplement)
          }
          case _: ScValue | _: ScVariable => {
            val typed: ScTyped = name match {case x: ScTyped => x case _ => return null}
            return ScalaPsiElementFactory.createOverrideImplementVariable(typed, subst, typed.getManager, !isImplement, 
              element match {case _: ScValue => true case _ => false})
          }
          case _ => return null
        }
      }
    }
  }

  def getAnchorAndPos(offset: Int, clazz: ScTypeDefinition): (Option[PsiElement], Int) = {
    val body = clazz.extendsBlock.templateBody match {
      case Some(x) => x
      case None => return (None, 0)
    }
    var element: PsiElement = body.getContainingFile.findElementAt(offset)
    while (element != null && element.getParent != body) element = element.getParent
    if (element != null)
      element.getNode.getElementType match {case ScalaTokenTypes.tLBRACE => element = element.getNextSibling case _ =>}
    val anchor: Option[PsiElement] = element match {case null => None case _ => Some(element)}
    val pos = element match {
      case null => 0
      case _: PsiWhiteSpace => offset - element.getTextRange.getStartOffset
      case _ => element.getNode.getElementType match {
        case ScalaTokenTypes.tLINE_TERMINATOR => offset - element.getTextRange.getStartOffset
        case _ => 0
      }
    }
    return (anchor, pos)
  }

  private def adjustTypesAndSetCaret(meth: PsiElement, editor: Editor): Unit = {
    if (meth != null) {
      ScalaPsiUtil.adjustTypes(meth)
      val body: PsiElement = meth match {
        case meth: ScTypeAliasDefinition => meth.aliasedTypeElement
        case meth: ScPatternDefinition => meth.expr
        case meth: ScVariableDefinition => meth.expr
        case method: ScFunctionDefinition => method.body match {
          case Some(x) => x
          case None => return
        }
        case _ => return
      }
      val offset = body.getTextRange.getStartOffset
      editor.getCaretModel.moveToOffset(offset)
      editor.getSelectionModel.setSelection(body.getTextRange.getStartOffset, body.getTextRange.getEndOffset)
    }
  }
}
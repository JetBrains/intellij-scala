package org.jetbrains.plugins.scala.overrideImplement

import com.intellij.openapi.editor.{Editor, VisualPosition}
import lang.psi.api.toplevel.ScModifierListOwner
import com.intellij.psi._
import lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement, ScFieldId}
import annotations.Nullable
import lang.psi.api.base.patterns.ScReferencePattern
import lang.psi.types.{ScType, PhysicalSignature, ScSubstitutor}
import lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import lang.psi.ScalaPsiElement
import lang.psi.api.base.types.ScSimpleTypeElement
import lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import lang.psi.api.statements._
import com.intellij.ide.highlighter.JavaFileType
import lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.ScalaUtils
import com.intellij.codeInsight.generation.ClassMember
import com.intellij.util.IncorrectOperationException
import com.intellij.codeInsight.generation.PsiFieldMember
import com.intellij.ide.util.MemberChooser
import com.intellij.codeInsight.generation.PsiMethodMember
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.codeInsight.generation.OverrideImplementUtil
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
    val candidates = if (isImplement) ScalaOIUtil.getMembersToImplement(clazz) else ScalaOIUtil.getMembersToOverride(clazz)
    if (candidates.isEmpty) return
    val classMembersBuf = new ArrayBuffer[ClassMember]
    for (candidate <- candidates) yield {
      candidate match {
        case x: PsiMethod => classMembersBuf += new PsiMethodMember(x)
        case x: ScTypeAlias => classMembersBuf += new PsiAliasMember(x)
        case x: ScReferencePattern => {
          valvarContext(x) match {
            case y: ScValue => classMembersBuf += new PsiValueMember(y, x)
            case y: ScVariable => classMembersBuf += new PsiVariableMember(y, x)
            case _ => {
              throw new IncorrectOperationException
              null
            }
          }
        }
        case x: ScFieldId => {
          valvarContext(x) match {
            case y: ScValue => classMembersBuf += new PsiValueMember(y, x)
            case y: ScVariable => classMembersBuf += new PsiVariableMember(y, x)
            case _ => {
              throw new IncorrectOperationException
              null
            }
          }
        }
        case x: PsiField => //todo: dont add now: classMembersBuf += new PsiFieldMember(x)
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
        case member: PsiMethodMember => {
          val method: PsiMethod = member.getElement
          ScalaUtils.runWriteAction(new Runnable {
            def run {
              val body = clazz.extendsBlock.templateBody match {
                case Some(x) => x
                case None => return
              }
              val brace = body.getFirstChild
              if (brace == null) return
              val anchor = brace.getNextSibling
              if (anchor == null) return
              val meth = ScalaPsiElementFactory.createOverrideImplementMethod(method, method.getManager, !isImplement)
              body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(meth.getManager), anchor.getNode)
              body.getNode.addChild(meth.getNode, anchor.getNode)  //todo: set caret into body
              meth match {
                case method: ScFunctionDefinition => {
                  val body = method.body match {
                    case Some(x) => x
                    case None => return
                  }
                  val offset = body.getTextRange.getStartOffset + 2
                  editor.getCaretModel.moveToOffset(offset)
                }
                case _ =>
              }
            }
          }, method.getProject, if (isImplement) "Implement method" else "Override method")
        }
        case member: PsiAliasMember => {
          val alias = member.getElement
          ScalaUtils.runWriteAction(new Runnable {
            def run {
              val body = clazz.extendsBlock.templateBody match {
                case Some(x) => x
                case None => return
              }
              val brace = body.getFirstChild
              if (brace == null) return
              val anchor = brace.getNextSibling
              if (anchor == null) return
              val meth = ScalaPsiElementFactory.createOverrideImplementType(alias, alias.getManager, !isImplement)
              body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(meth.getManager), anchor.getNode)
              body.getNode.addChild(meth.getNode, anchor.getNode) //todo: set selection over body
            }
          }, alias.getProject, if (isImplement) "Implement type alias" else "Override type alias")
        }
        case member: PsiValueMember => {
          val value = member.element
          ScalaUtils.runWriteAction(new Runnable {
            def run {
              val body = clazz.extendsBlock.templateBody match {
                case Some(x) => x
                case None => return
              }
              val brace = body.getFirstChild
              if (brace == null) return
              val anchor = brace.getNextSibling
              if (anchor == null) return
              val meth = ScalaPsiElementFactory.createOverrideImplementVariable(value, value.getManager, !isImplement, true)
              body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(meth.getManager), anchor.getNode)
              body.getNode.addChild(meth.getNode, anchor.getNode) //todo: set selection over body
            }
          }, value.getProject, if (isImplement) "Implement value" else "Override value")
        }
        case member: PsiVariableMember => {
          val variable = member.element
          ScalaUtils.runWriteAction(new Runnable {
            def run {
              val body = clazz.extendsBlock.templateBody match {
                case Some(x) => x
                case None => return
              }
              val brace = body.getFirstChild
              if (brace == null) return
              val anchor = brace.getNextSibling
              if (anchor == null) return
              val meth = ScalaPsiElementFactory.createOverrideImplementVariable(variable, variable.getManager, !isImplement, false)
              body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(meth.getManager), anchor.getNode)
              body.getNode.addChild(meth.getNode, anchor.getNode) //todo: set selection over body
            }
          }, variable.getProject, if (isImplement) "Implement variable" else "Override variable")
        }
        case member: PsiFieldMember => {
          //todo: I think scala don't perform to override java fields
        }
        case _ =>
      }
    }
  }

  def getMembersToOverride(clazz: ScTypeDefinition): Array[PsiElement] = {
    val buf = new ArrayBuffer[PsiElement]
    buf ++= (for (key <- TypeDefinitionMembers.getMethods(clazz).keys) yield key.method)
    buf ++= (for (key <- TypeDefinitionMembers.getTypes(clazz).keys) yield key)
    buf ++= (for (key <- TypeDefinitionMembers.getVals(clazz).keys) yield key)
    val buf2 = new ArrayBuffer[PsiElement]
    for (element <- buf) {
      element match {
        case _: PsiClass =>
        case x: PsiMethod if x.getName == "$tag" =>
        case x: PsiMember if x.getContainingClass == clazz =>
        case x: PsiMember if x.getContainingClass.isInterface =>
        case x: ScReferencePattern => valvarContext(x) match {
          case x: ScPatternDefinition if x.getContainingClass != clazz => buf2 += element
          case x: ScVariableDefinition if x.getContainingClass != clazz => buf2 += element
          case _ =>
        }
        case x: ScFieldId => valvarContext(x) match {
          case x: ScPatternDefinition if x.getContainingClass != clazz => buf2 += element
          case x: ScVariableDefinition if x.getContainingClass != clazz => buf2 += element
          case _ =>
        }
        case x: ScValueDeclaration =>
        case x: ScVariableDeclaration =>
        case x: ScTypeAliasDeclaration =>
        case x: ScFunctionDeclaration =>
        case x: PsiModifierListOwner if x.hasModifierProperty("abstract")
            || x.hasModifierProperty("final") =>
        case x: PsiMethod if x.isConstructor =>
        case x: PsiMethod => {
          var flag = false
          for (method <- clazz.getMethods) {
            if (compare(x, method)) flag = true
          }
          if (!flag) buf2 += element
        }
        case _ => buf2 += element
      }
    }
    return buf2.toArray
  }

  def getMembersToImplement(clazz: ScTypeDefinition): Array[PsiElement] = {
    val buf = new ArrayBuffer[PsiElement]
    buf ++= (for (key <- TypeDefinitionMembers.getMethods(clazz).keys) yield key.method)
    buf ++= (for (key <- TypeDefinitionMembers.getTypes(clazz).keys) yield key)
    buf ++= (for (key <- TypeDefinitionMembers.getVals(clazz).keys) yield key)
    val buf2 = new ArrayBuffer[PsiElement]
    for (element <- buf) {
      def addMethod(x: PsiMethod) {
        var flag = false
        for (method <- buf) {
          method match {
            case x: PsiMethod if x.getName == "$tag" =>
            case x: PsiMember if x.getContainingClass.isInterface =>
            case _: ScValueDeclaration =>
            case _: ScVariableDeclaration =>
            case _: ScTypeAliasDeclaration =>
            case _: ScFunctionDeclaration =>
            case x: PsiMethod if x.getModifierList.hasModifierProperty("abstract") =>
            case x: PsiMethod if x.isConstructor =>
            case method: PsiMethod => if (compare(x, method)) flag = true
            case _ =>
          }
        }
        if (!flag) buf2 += element
      }
      element match {
        case _: PsiClass =>
        case x: PsiMember if x.getContainingClass == clazz =>
        case x: PsiMethod if x.getName == "$tag" =>
        case x: PsiMethod if x.getContainingClass.isInterface => addMethod(x)
        case x: ScReferencePattern => valvarContext(x) match {
          case x: ScValueDeclaration if x.getContainingClass != clazz => buf2 += element
          case x: ScVariableDeclaration if x.getContainingClass != clazz => buf2 += element
          case _ =>
        }
        case x: ScFieldId => valvarContext(x) match {
          case x: ScValueDeclaration if x.getContainingClass != clazz => buf2 += element
          case x: ScVariableDeclaration if x.getContainingClass != clazz => buf2 += element
          case _ =>
        }
        case x: ScTypeAliasDeclaration => buf2 += element
        case x: ScFunctionDeclaration => addMethod(x)
        case x: PsiMethod if x.hasModifierProperty("abstract") => addMethod(x)
        case _ =>
      }
    }
    return buf2.toArray
  }

  private def compare(method1: PsiMethod, method2: PsiMethod): Boolean = {
    val signature1 = new PhysicalSignature(method1, ScSubstitutor.empty)
    val signature2 = new PhysicalSignature(method2, ScSubstitutor.empty)
    return signature1.equiv(signature2)
  }

  @Nullable
  private def valvarContext(x: PsiElement): PsiElement = {
    var parent = x.getParent
    while (parent != null && !parent.isInstanceOf[ScValue] && !parent.isInstanceOf[ScVariable]) parent = parent.getParent
    return parent
  }
}
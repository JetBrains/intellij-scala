package org.jetbrains.plugins.scala.overrideImplement

import lang.psi.types.ScType
import lang.psi.ScalaPsiElement
import lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import lang.psi.api.base.types.ScSimpleTypeElement
import lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import lang.psi.api.statements._
import com.intellij.ide.highlighter.JavaFileType
import lang.psi.impl.ScalaPsiElementFactory
import util.ScalaUtils
import com.intellij.psi.PsiMember
import com.intellij.codeInsight.generation.ClassMember
import com.intellij.util.IncorrectOperationException
import com.intellij.codeInsight.generation.PsiFieldMember
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiClass
import com.intellij.ide.util.MemberChooser
import com.intellij.codeInsight.generation.PsiMethodMember
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.codeInsight.generation.OverrideImplementUtil
import com.intellij.psi.infos.CandidateInfo
import lang.psi.api.toplevel.typedef.ScTypeDefinition
import com.intellij.psi.PsiElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.07.2008
 */

object ScalaOIUtil {
  def invokeOverride(project: Project, editor: Editor, file: PsiFile) {
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
    val candidates = ScalaOIUtil.getMembersToOverride(clazz)
    if (candidates.isEmpty) return
    val classMembersBuf = new ArrayBuffer[ClassMember]
    for (candidate <- candidates) yield {
      candidate match {
        case x: PsiMethod => classMembersBuf += new PsiMethodMember(x)
        case x: ScTypeAlias => classMembersBuf += new PsiAliasMember(x)
        case x: ScValue => classMembersBuf ++= (for (element <- x.declaredElements) yield new PsiValueMember(x, element))
        case x: ScVariable => classMembersBuf ++= (for (element <- x.declaredElements) yield new PsiVariableMember(x, element))
        case x: PsiField => classMembersBuf += new PsiFieldMember(x)
        case _ => {
          throw new IncorrectOperationException
          null
        }
      }
    }
    val classMembers = classMembersBuf.toArray
    val chooser = new MemberChooser[ClassMember](classMembers, false, true, project)
    chooser.setTitle(ScalaBundle.message("select.method.override", Array[Object]()))
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
              val meth = ScalaPsiElementFactory.createOverrideImplementMethod(method, method.getManager, true)
              body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(meth.getManager), anchor.getNode)
              body.getNode.addChild(meth.getNode, anchor.getNode)
            }
          }, method.getProject, "Override method")
        }
        case member: PsiAliasMember => {

        }
        case member: PsiValueMember => {

        }
        case member: PsiVariableMember => {

        }
        case member: PsiFieldMember => {

        }
        case _ =>
      }
    }
  }

  def invokeImplement(project: Project, editor: Editor, file: PsiFile) {
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
    val candidates = ScalaOIUtil.getMembersToImplement(clazz)
    if (candidates.isEmpty) return
    val classMembersBuf = new ArrayBuffer[ClassMember]
    for (candidate <- candidates) yield {
      candidate match {
        case x: PsiMethod => classMembersBuf += new PsiMethodMember(x)
        case x: ScTypeAlias => classMembersBuf += new PsiAliasMember(x)
        case x: ScValue => classMembersBuf ++= (for (element <- x.declaredElements) yield new PsiValueMember(x, element))
        case x: ScVariable => classMembersBuf ++= (for (element <- x.declaredElements) yield new PsiVariableMember(x, element))
        case x: PsiField => classMembersBuf += new PsiFieldMember(x)
        case _ => {
          throw new IncorrectOperationException
          null
        }
      }
    }
    val classMembers = classMembersBuf.toArray
    val chooser = new MemberChooser[ClassMember](classMembers, false, true, project)
    chooser.setTitle(ScalaBundle.message("select.method.override", Array[Object]()))
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
              val meth = ScalaPsiElementFactory.createOverrideImplementMethod(method, method.getManager, false)
              body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(meth.getManager), anchor.getNode)
              body.getNode.addChild(meth.getNode, anchor.getNode)
            }
          }, method.getProject, "Implement method")
        }
        case member: PsiAliasMember => {

        }
        case member: PsiValueMember => {

        }
        case member: PsiVariableMember => {

        }
        case member: PsiFieldMember => {

        }
        case _ =>
      }
    }
  }

  def getMembersToOverride(clazz: ScTypeDefinition): Array[PsiElement] = {
    val buf = new ArrayBuffer[PsiElement]
    buf ++= clazz.getAllMethods
    buf ++= clazz.allAliases
    buf ++= clazz.allVals
    buf ++= clazz.allVars
    buf ++= clazz.allFields
    val buf2 = new ArrayBuffer[PsiElement]
    for (element <- buf) {
      element match {
        case x: PsiMember if x.getContainingClass == clazz =>
        case x: PsiMember if x.getContainingClass.isInterface =>
        case x: ScValueDeclaration =>
        case x: ScVariableDeclaration =>
        case x: ScTypeAliasDeclaration =>
        case x: ScFunctionDeclaration =>
        case x: PsiMethod if x.getModifierList.hasModifierProperty("abstract") =>
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
    buf ++= clazz.getAllMethods
    buf ++= clazz.allAliases
    buf ++= clazz.allVals
    buf ++= clazz.allVars
    buf ++= clazz.allFields
    val buf2 = new ArrayBuffer[PsiElement]
    for (element <- buf) {
      def addMethod(x: PsiMethod) {
        var flag = false
        for (method <- buf) {
          method match {
            case _: PsiMember if x.getContainingClass == clazz =>
            case _: PsiMember if x.getContainingClass.isInterface =>
            case _: ScValueDeclaration =>
            case _: ScVariableDeclaration =>
            case _: ScTypeAliasDeclaration =>
            case _: ScFunctionDeclaration =>
            case _: PsiMethod if x.getModifierList.hasModifierProperty("abstract") =>
            case _: PsiMethod if x.isConstructor =>
            case method: PsiMethod => if (compare(x, method)) flag = true
            case _ =>
          }
        }
        if (!flag) buf2 += element
      }
      element match {
        case x: PsiMethod if x.getContainingClass.isInterface => addMethod(x)
        case x: ScValueDeclaration => buf2 += element
        case x: ScVariableDeclaration => buf2 += element
        case x: ScTypeAliasDeclaration => buf2 += element
        case x: ScFunctionDeclaration => addMethod(x)
        case x: PsiMethod if x.getModifierList.hasModifierProperty("abstract") => addMethod(x)
        case _ =>
      }
    }
    return buf2.toArray
  }

  private def compare(method1: PsiMethod, method2: PsiMethod): Boolean = {
    if (method1.getName != method2.getName) return false
    val n = method1.getParameterList.getParametersCount
    val m = method2.getParameterList.getParametersCount
    if (n != m) return false
    var i = 0
    while (i < n) {
      val type1: ScType = method1 match {
        case method: ScFunction => {
          method.parameters(i).calcType
        }
        case method: PsiMethod => {
          val type3 = method.getParameterList.getParameters.apply(i).getTypeElement.getType
          ScType.create(type3, method.getProject)
        }
      }
      val type2: ScType = method2 match {
        case method: ScFunction => {
          method.parameters(i).calcType
        }
        case method: PsiMethod => {
          val type3 = method.getParameterList.getParameters.apply(i).getTypeElement.getType
          ScType.create(type3, method.getProject)
        }
      }
      if (!type1.equiv(type2)) return false
      i = i + 1
    }
    return true
  }
}
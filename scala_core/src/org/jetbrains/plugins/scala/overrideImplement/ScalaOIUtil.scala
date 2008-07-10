package org.jetbrains.plugins.scala.overrideImplement

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
    val candidates = ScalaOIUtil.getMembersToOverrideImplement(clazz, false)
    if (candidates.isEmpty) return
    val classMembers: Array[ClassMember] = for (candidate <- candidates) yield {
      candidate.getElement match {
        case _: PsiMethod => new PsiMethodMember(candidate)
        case x: PsiField => new PsiFieldMember(x) //todo: definitions is not PsiField, must be changed
        case _ => {
          throw new IncorrectOperationException
          null
        }
      }
    }
    val chooser = new MemberChooser[ClassMember](classMembers, false, true, project)
    chooser.setTitle(ScalaBundle.message("select.method.override", Array[Object]()))
    chooser.show
  }

  def invokeImplement(project: Project, editor: Editor, file: PsiFile) {

  }

  def getMembersToOverrideImplement(clazz: PsiClass, isImplement: Boolean): Array[CandidateInfo] = {
    val buf = new ArrayBuffer[CandidateInfo]
    buf ++= (for (c <- clazz.getAllMethods) yield new CandidateInfo(c, PsiSubstitutor.EMPTY))
    buf ++= (for (c <- clazz.getAllFields) yield new CandidateInfo(c, PsiSubstitutor.EMPTY)) 
    return buf.toArray
  }
}
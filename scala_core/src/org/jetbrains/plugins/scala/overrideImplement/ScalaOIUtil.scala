package org.jetbrains.plugins.scala.overrideImplement

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
  def invokeOverride(project :Project, editor: Editor, file: PsiFile) {
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
    val methodsToOverrideImplement = OverrideImplementUtil.getMethodsToOverrideImplement(clazz, false)
    val candidates = methodsToOverrideImplement.toArray(new Array[CandidateInfo](methodsToOverrideImplement.size))
    if (candidates.isEmpty) return
    val classMembers = for (candidate <- candidates) yield new PsiMethodMember(candidate)
    val chooser = new MemberChooser[PsiMethodMember](classMembers, false, true, project)
    chooser.setTitle(ScalaBundle.message("select.method.override", Array[Object]()))
    chooser.show
  }

  def invokeImplement(project :Project, editor: Editor, file: PsiFile) {

  }
}
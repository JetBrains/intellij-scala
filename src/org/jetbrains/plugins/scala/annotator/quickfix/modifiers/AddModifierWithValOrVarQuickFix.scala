package org.jetbrains.plugins.scala
package annotator.quickfix.modifiers

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Nikolay.Tropin
 * 2014-06-24
 */
class AddModifierWithValOrVarQuickFix(member: ScModifierListOwner, modifier: String, addVal: Boolean)
        extends AddModifierQuickFix(member, modifier) {
  override def invoke(project: Project, editor: Editor, file: PsiFile) = {
    val psiKeyword =
      if (addVal) {
        val decl = ScalaPsiElementFactory.createDeclarationFromText("val x", member.getParent, member)
        decl.findFirstChildByType(ScalaTokenTypes.kVAL)
      }
      else {
        val decl = ScalaPsiElementFactory.createDeclarationFromText("var x", member.getParent, member)
        decl.findFirstChildByType(ScalaTokenTypes.kVAR)
      }
    member.addAfter(psiKeyword, member.getModifierList)
    member.setModifierProperty(modifier, true)
  }
  
}
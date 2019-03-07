package org.jetbrains.plugins.scala
package annotator
package quickfix

import com.intellij.codeInsight._
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

sealed abstract class ModifierQuickFix(listOwner: ScModifierListOwner)
                                      (key: String, nameId: PsiElement)
                                      (modifier: ScalaModifier, value: Boolean)
  extends intention.IntentionAction {

  override final def isAvailable(project: Project,
                                 editor: Editor,
                                 file: PsiFile): Boolean =
    listOwner.isValid &&
      listOwner.getManager.isInProject(file)

  override final def invoke(project: Project,
                            editor: Editor,
                            file: PsiFile): Unit = listOwner.getModifierList match {
    case null => ModifierQuickFix.logger.error("Illegal modifier list in: " + listOwner)
    case modifierList => onModifierList(modifierList)(project, file)
  }

  protected def onModifierList(modifierList: ScModifierList)
                              (implicit project: Project, file: PsiFile): Unit = {
    modifierList.setModifierProperty(modifier.text, value)
  }

  override final def startInWriteAction = true

  override final val getFamilyName: String = {
    val modifierText = modifier.text
    nameId match {
      case null => ScalaBundle.message(key, modifierText)
      case id => daemon.QuickFixBundle.message(key, id.getText, modifierText)
    }
  }

  override final def getText: String = getFamilyName
}

object ModifierQuickFix {

  private val logger = Logger.getInstance(getClass)

  final class Remove(listOwner: ScModifierListOwner, nameId: PsiElement, modifier: ScalaModifier)
    extends ModifierQuickFix(listOwner)("remove.modifier.fix", nameId)(modifier, value = false) {

    override def onModifierList(modifierList: ScModifierList)
                               (implicit project: Project, file: PsiFile): Unit = {
      super.onModifierList(modifierList)

      // Should be handled by auto formatting
      val textRange = modifierList.getTextRange
      CodeStyleManager.getInstance(project).reformatText(
        file,
        textRange.getStartOffset,
        textRange.getEndOffset
      )
    }
  }

  sealed class Add(listOwner: ScModifierListOwner, nameId: PsiElement, modifier: ScalaModifier)
    extends ModifierQuickFix(listOwner)("add.modifier.fix", nameId)(modifier, value = true)

  final class AddWithKeyword(listOwner: ScModifierListOwner, nameId: PsiElement, keywordType: IElementType)
    extends Add(listOwner, nameId, ScalaModifier.Override) {

    override def onModifierList(modifierList: ScModifierList)
                               (implicit project: Project, file: PsiFile): Unit = {
      val keyword = ScalaPsiElementFactory.createDeclarationFromText(
        keywordType.toString + " x",
        listOwner.getParent,
        listOwner
      ).findFirstChildByType(keywordType)
      listOwner.addAfter(keyword, modifierList)

      super.onModifierList(modifierList)
    }
  }

}
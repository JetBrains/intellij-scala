package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight._
import com.intellij.modcommand.{ActionContext, ModCommand, PsiBasedModCommandAction}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.{Nls, Nullable}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

import java.util.Collections


sealed class ModifierQuickFix(listOwner: ScModifierListOwner)
                              (@Nls modifierToText: String => String)
                              (modifier: ScalaModifier, value: Boolean = false)
  extends PsiBasedModCommandAction[ScModifierListOwner](listOwner)
{
  override def perform(context: ActionContext, listOwner: ScModifierListOwner): ModCommand = {
    listOwner.getModifierList match {
      case null =>
        ModifierQuickFix.logger.error("Illegal modifier list in: " + listOwner)
        ModCommand.nop()
      case modifierList =>
        ModCommand.psiUpdate(modifierList, (modifierList: ScModifierList) => onModifierList(modifierList))
    }
  }

  override def getFamilyName: String = modifierToText(modifier.text)

  protected def onModifierList(modifierList: ScModifierList): Unit = {
     modifierList.setModifierProperty(modifier.text, value)
  }
}

object ModifierQuickFix {

  import ScalaModifier._

  private val logger = Logger.getInstance(getClass)

  final class Remove(listOwner: ScModifierListOwner, nameId: PsiElement, modifier: ScalaModifier)
    extends ModifierQuickFix(listOwner)(
      if (nameId == null) ScalaBundle.message("remove.named.modifier.fix", _)
      else daemon.QuickFixBundle.message("remove.modifier.fix", nameId.getText, _)
    )(modifier) {

    override def onModifierList(modifierList: ScModifierList): Unit = {
      super.onModifierList(modifierList)

      // Should be handled by auto formatting
      val textRange = modifierList.getTextRange
      val codeStyleManager = CodeStyleManager.getInstance(modifierList.getProject)
      if (textRange.isEmpty)
        codeStyleManager.adjustLineIndent(modifierList.getContainingFile, textRange)
      else
        codeStyleManager.reformatText(modifierList.getContainingFile, Collections.singleton(textRange))
    }
  }

  sealed class Add(listOwner: ScModifierListOwner, @Nullable nameId: PsiElement, modifier: ScalaModifier)
    extends ModifierQuickFix(listOwner)(
      if (nameId == null) ScalaBundle.message("add.modifier.fix.without.name", _)
      else daemon.QuickFixBundle.message("add.modifier.fix", nameId.getText, _)
    )(modifier, value = true)

  final class AddWithKeyword(listOwner: ScModifierListOwner, nameId: PsiElement, keywordType: IElementType)
    extends Add(listOwner, nameId, Override) {

    override def onModifierList(modifierList: ScModifierList): Unit = {
      val keyword = ScalaPsiElementFactory.createDeclarationFromText(
        keywordType.toString + " x",
        listOwner.getParent,
        listOwner
      ).findFirstChildByType(keywordType).get
      listOwner.addAfter(keyword, modifierList)

      super.onModifierList(modifierList)
    }
  }

  sealed abstract class MakeNonPrivate(listOwner: ScModifierListOwner, @Nls modifierToText: String => String)
                                      (modifier: ScalaModifier, value: Boolean)
    extends ModifierQuickFix(listOwner)(modifierToText)(modifier, value) {

    override protected def onModifierList(modifierList: ScModifierList): Unit = {
      super.onModifierList(modifierList)
      CodeStyleManager.getInstance(modifierList.getProject)
        .adjustLineIndent(modifierList.getContainingFile, modifierList.getTextRange.getStartOffset)
    }

    override def getFamilyName: String = ScalaBundle.message("make.non.private.title")
  }

  final class MakePublic(listOwner: ScModifierListOwner)
    extends MakeNonPrivate(listOwner, ScalaBundle.message("make.public.fix", _))(Private, false)

  final class MakeProtected(listOwner: ScModifierListOwner)
    extends MakeNonPrivate(listOwner, ScalaBundle.message("make.protected.fix", _))(Protected, value = true) {

    override protected def onModifierList(modifierList: ScModifierList): Unit = {
      modifierList.setModifierProperty(PRIVATE, false)
      super.onModifierList(modifierList)
    }
  }
}

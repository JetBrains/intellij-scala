package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight._
import com.intellij.modcommand.{ActionContext, ModCommand, PsiBasedModCommandAction}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
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

sealed abstract class ModifierQuickFix(listOwner: ScModifierListOwner)
  extends PsiBasedModCommandAction[ScModifierListOwner](listOwner)
    with DumbAware {
  override def perform(context: ActionContext, listOwner: ScModifierListOwner): ModCommand = {
    listOwner.getModifierList match {
      case null =>
        ModifierQuickFix.logger.error("Illegal modifier list in: " + listOwner)
        ModCommand.nop()
      case modifierList =>
        ModCommand.psiUpdate(modifierList, (modifierList: ScModifierList) => onModifierList(modifierList))
    }
  }

  protected def onModifierList(modifierList: ScModifierList): Unit
}

object ModifierQuickFix {

  import ScalaModifier._

  private val logger = Logger.getInstance(getClass)

  private def formatModifierList(modifierList: ScModifierList): Unit = {
    val textRange = modifierList.getTextRange
    val codeStyleManager = CodeStyleManager.getInstance(modifierList.getProject)
    if (textRange.isEmpty)
      codeStyleManager.adjustLineIndent(modifierList.getContainingFile, textRange)
    else
      codeStyleManager.reformatText(modifierList.getContainingFile, Collections.singleton(textRange))
  }

  final class RemoveSpecific(listOwner: ScModifierListOwner, modifierElement: PsiElement)
    extends ModifierQuickFix(listOwner) {

    private val modifierStartOffsetInList = modifierElement.getNode.getStartOffsetInParent

    override protected def onModifierList(modifierList: ScModifierList): Unit = {
      val node = modifierList.getNode
      node.getChildren(null)
        .find(_.getStartOffsetInParent == modifierStartOffsetInList)
        .foreach(node.removeChild)

      // Should be handled by auto formatting
      formatModifierList(modifierList)
    }

    override def getFamilyName: String = ScalaBundle.message("remove.named.modifier.fix", modifierElement.getText)
  }

  final class Remove(listOwner: ScModifierListOwner, @Nullable nameId: PsiElement, modifier: ScalaModifier) extends ModifierQuickFix(listOwner) {
    override def onModifierList(modifierList: ScModifierList): Unit = {
      modifierList.setModifierProperty(modifier.text, false)

      // Should be handled by auto formatting
      formatModifierList(modifierList)
    }

    override def getFamilyName: String =
      if (nameId == null) ScalaBundle.message("remove.named.modifier.fix", modifier.text)
      else daemon.QuickFixBundle.message("remove.modifier.fix", nameId.getText, modifier.text)
  }

  sealed class Add(listOwner: ScModifierListOwner, @Nullable nameId: PsiElement, modifier: ScalaModifier)
    extends ModifierQuickFix(listOwner) {

    override def onModifierList(modifierList: ScModifierList): Unit = {
      modifierList.setModifierProperty(modifier.text, true)
    }

    override def getFamilyName: String =
      if (nameId == null) ScalaBundle.message("add.modifier.fix.without.name", modifier.text)
      else daemon.QuickFixBundle.message("add.modifier.fix", nameId.getText, modifier.text)
  }

  final class AddOverrideWithKeyword(listOwner: ScModifierListOwner, nameId: PsiElement, keywordType: IElementType)
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

  sealed abstract class MakeNonPrivate(listOwner: ScModifierListOwner,
                                       @Nls override val getFamilyName: String,
                                       modifier: String)
    extends ModifierQuickFix(listOwner) {

    override protected def onModifierList(modifierList: ScModifierList): Unit = {
      modifierList.setModifierProperty(modifier, true)
      CodeStyleManager.getInstance(modifierList.getProject)
        .adjustLineIndent(modifierList.getContainingFile, modifierList.getTextRange.getStartOffset)
    }
  }

  final class MakePublic(listOwner: ScModifierListOwner)
    extends MakeNonPrivate(listOwner, ScalaBundle.message("make.public.fix"), "public")

  final class MakeProtected(listOwner: ScModifierListOwner)
    extends MakeNonPrivate(listOwner, ScalaBundle.message("make.protected.fix"), Protected.text)
}

package org.jetbrains.plugins.scala.overrideImplement

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.generation.GenerateMembersUtil
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.editor.enterHandler.EnterHandlerUtils
import org.jetbrains.plugins.scala.editor.{ScalaEditorUtils, ScalaIndentationSyntaxUtils}
import org.jetbrains.plugins.scala.extensions.{&, ElementType, Parent, PrevElement, PsiFileExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

import scala.jdk.CollectionConverters.{ListHasAsScala, SeqHasAsJava}

private[scala]
object ScalaGenerateMembersUtil {

  def insertMembersAtCaretPosition(
    members: Seq[ClassMember],
    templateDefinition: ScTemplateDefinition,
    editor: Editor,
    addOverrideModifierIfEnforcedBySettings: Boolean
  ): Seq[ScalaGenerationInfo] = {
    val sortedMembers = ScalaMemberChooser.sortedWithExtensionMethodsGrouped(members, templateDefinition).reverse
    val genInfos = sortedMembers.map { member =>
      val needsOverrideModifier = member match {
        case overridable: ScalaOverridableMember =>
          overridable.isOverride || addOverrideModifierIfEnforcedBySettings && ScalaGenerationInfo.toAddOverrideToImplemented
        case _ =>
          false
      }
      new ScalaGenerationInfo(member, needsOverrideModifier)
    }
    val caretOffset = ScalaEditorUtils.caretOffsetWithFixedEof(editor)
    val inserted = GenerateMembersUtil.insertMembersAtOffset(templateDefinition, caretOffset, genInfos.asJava)
    inserted.asScala.toSeq
  }

  def positionCaret(editor: Editor, inserted: Seq[ScalaGenerationInfo]): Unit = {
    inserted.lastOption.foreach(_.positionCaret(editor, toEditMethodBody = true))
  }

  def getElementAtCaretAdjustedForIndentationBasedSyntax(file: PsiFile, editor: Editor): PsiElement = {
    val caretOffset = editor.getCaretModel.getOffset
    val elementAtCaret = ScalaEditorUtils.findElementAtCaret_WithFixedEOF(file, editor.getDocument, caretOffset)
    val elementAtCaretFixed = elementAtCaret match {
      case ws: PsiWhiteSpace if caretOffset == ws.getNode.getStartOffset =>
        // in case when caret is right after the error end offset
        PsiTreeUtil.prevLeaf(ws)
      case (_: PsiWhiteSpace) & PrevElement(colon@ElementType(ScalaTokenTypes.tCOLON) & Parent(body: ScTemplateBody))
        if file.isScala3File && body.exprs.isEmpty =>
        // in case when caret is inside an empty template body in Scala 3 indentation-based syntax
        colon
      case ws: PsiWhiteSpace if file.isScala3File =>
        //When we are in the end of indentation-based syntax template body
        //we need to take into account current current indentation level when choosing template definition
        val adjusted = getPreviousElementInSameIndentationLevel(file, editor, caretOffset, elementAtCaret)
        adjusted.getOrElse(ws)
      case e => e
    }
    elementAtCaretFixed
  }

  private def getPreviousElementInSameIndentationLevel(
    file: PsiFile,
    editor: Editor,
    caretOffset: Int,
    elementAtCaret: PsiElement
  ): Option[PsiElement] = {
    val indentOptions = CodeStyle.getIndentOptions(file)
    val caretIndent = EnterHandlerUtils.calcCaretIndent(caretOffset, editor.getDocument.getCharsSequence, indentOptions.TAB_SIZE)
    val caretIndentSize = caretIndent.getOrElse(Int.MaxValue) // using MaxValue if the caret isn't inside code indent
    val prevElementWithSameIndent = ScalaIndentationSyntaxUtils.previousElementInIndentationContext(elementAtCaret, caretIndentSize, indentOptions)
    prevElementWithSameIndent.map(_._1)
  }
}

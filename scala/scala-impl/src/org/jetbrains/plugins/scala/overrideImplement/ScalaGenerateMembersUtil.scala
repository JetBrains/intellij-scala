package org.jetbrains.plugins.scala.overrideImplement

import com.intellij.codeInsight.generation.GenerateMembersUtil
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.editor.ScalaEditorUtils
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
}

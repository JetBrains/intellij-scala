package org.jetbrains.plugins.scala
package codeInsight.generation

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.TypeAdjuster
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

/**
 * Nikolay.Tropin
 * 2014-09-18
 */
class ScalaGeneratePropertyHandler extends LanguageCodeInsightActionHandler {
  override def isValidFor(editor: Editor, file: PsiFile): Boolean = {
    def isOnVar: Boolean = {
      GenerationUtil.elementOfTypeAtCaret(editor, file, classOf[ScVariableDefinition]) match {
        case Some(v) if v.isSimple && v.containingClass != null => true
        case _ => false
      }
    }

    file != null && ScalaFileType.INSTANCE == file.getFileType && isOnVar
  }

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    val varDef = GenerationUtil.elementOfTypeAtCaret(editor, file, classOf[ScVariableDefinition]) match {
      case Some(x) if x.isSimple && x.containingClass != null => x
      case _ => return
    }

    addPropertyMembers(varDef)
    varDef.delete()
  }

  private def addPropertyMembers(varDef: ScVariableDefinition) = {
    val name = varDef.bindings.head.name
    val typeText = varDef.getType(TypingContext.empty).getOrAny.canonicalText
    val defaultValue = varDef.expr.fold("???")(_.getText)
    val modifiers = varDef.getModifierList.getText

    def createDefinition(text: String) =
      createDefinitionWithContext(text, varDef.getContext, varDef)

    val backingVarText = s"private[this] var _$name: $typeText = $defaultValue"
    val backingVar_0 = createDefinition(backingVarText)

    val getterText = s"$modifiers def $name: $typeText = _$name"
    val getter_0 = createDefinition(getterText)

    val setterText =
      s"""$modifiers def ${name}_=(value: $typeText): Unit = {
        |  _$name = value
        |}""".stripMargin.replace("\r", "")
    val setter_0 = createDefinition(setterText)

    val parent = varDef.getParent
    val added = Seq(backingVar_0, getter_0, setter_0).map { elem =>
      parent.addBefore(createNewLine()(varDef.getManager), varDef)
      parent.addBefore(elem, varDef)
    }
    TypeAdjuster.adjustFor(added)
  }

  override def startInWriteAction(): Boolean = true
}

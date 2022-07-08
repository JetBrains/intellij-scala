package org.jetbrains.plugins.scala
package codeInsight
package generation
package actions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.lang.psi.TypeAdjuster
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createDefinitionWithContext, createNewLine}
import org.jetbrains.plugins.scala.lang.psi.types.result._

final class ScalaGeneratePropertyAction extends ScalaBaseGenerateAction(
  new ScalaGeneratePropertyAction.Handler,
  ScalaCodeInsightBundle.message("generate.proprty.action.text"),
  ScalaCodeInsightBundle.message("generate.proprty.action.description")
)

object ScalaGeneratePropertyAction {

  private[generation] final class Handler extends ScalaCodeInsightActionHandler {

    import Handler._

    override def isValidFor(editor: Editor, file: PsiFile): Boolean =
      super.isValidFor(editor, file) && findValidVariableDefinition(editor, file).isDefined

    override def invoke(project: Project,
                        editor: Editor,
                        file: PsiFile): Unit =
      findValidVariableDefinition(editor, file).foreach { definition =>
        addPropertyMembers(definition)
        definition.delete()
      }
  }

  private object Handler {

    private def findValidVariableDefinition(implicit editor: Editor, file: PsiFile) =
      elementOfTypeAtCaret(classOf[ScVariableDefinition]).find(isVariableDefinitionValid)

    private[this] def isVariableDefinitionValid(definition: ScVariableDefinition): Boolean =
      definition.isSimple && definition.containingClass != null

    private def addPropertyMembers(definition: ScVariableDefinition): Unit = {
      val name = definition.bindings.head.name
      val typeText = definition.`type`().getOrAny.canonicalText
      val defaultValue = definition.expr.fold("???")(_.getText)
      val modifiers = definition.getModifierList.getText

      def createDefinition(text: String) =
        createDefinitionWithContext(text, definition.getContext, definition)

      val backingVarText = s"private[this] var _$name: $typeText = $defaultValue"
      val backingVar_0 = createDefinition(backingVarText)

      val getterText = s"$modifiers def $name: $typeText = _$name"
      val getter_0 = createDefinition(getterText)

      val setterText =
        s"""$modifiers def ${name}_=(value: $typeText): Unit = {
           |  _$name = value
           |}""".stripMargin.replace("\r", "")
      val setter_0 = createDefinition(setterText)

      val parent = definition.getParent
      val added = Seq(backingVar_0, getter_0, setter_0).map { elem =>
        parent.addBefore(createNewLine()(definition.getManager), definition)
        parent.addBefore(elem, definition)
      }
      TypeAdjuster.adjustFor(added)
    }
  }

}

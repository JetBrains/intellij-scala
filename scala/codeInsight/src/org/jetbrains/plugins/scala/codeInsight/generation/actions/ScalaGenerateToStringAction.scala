package org.jetbrains.plugins.scala
package codeInsight
package generation
package actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
  * Generate toString metod action.
  */
final class ScalaGenerateToStringAction extends ScalaBaseGenerateAction(
  new ScalaGenerateToStringAction.Handler,
  ScalaCodeInsightBundle.message("generate.tostring.method.action.text"),
  ScalaCodeInsightBundle.message("generate.tostring.method.action.description")
)

object ScalaGenerateToStringAction {

  private[generation] final class Handler extends ScalaCodeInsightActionHandler {

    import Handler._

    override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
      if (!FileDocumentManager.getInstance.requestWriting(editor.getDocument, project)) return

      for {
        definition <- findTypeDefinition(editor, file)

        (fields, withFieldNames) <- showWizard(definition)(project)

        fieldsText = fields.map { field =>
          val fieldName = field.name
          val prefix = if (withFieldNames) fieldName + "=" else ""

          prefix + "$" + fieldName
        }.commaSeparated(model = Model.Parentheses)

        typeName = strippedName(definition)
        methodText = s"""override def toString = s"$typeName$fieldsText""""

        toStringMethod = ScalaPsiElementFactory.createMethodWithContext(methodText, definition, definition.extendsBlock)
      } inWriteAction {
        addMembers(definition, toStringMethod :: Nil, editor.getDocument)
      }
    }

    /**
      * Determines whether toString can be generated for the class.
      */
    override def isValidFor(editor: Editor, file: PsiFile): Boolean =
      super.isValidFor(editor, file) &&
        findTypeDefinition(editor, file).exists {
          case _: ScTrait => true
          case definition => !definition.isCase
        }

    override def startInWriteAction(): Boolean = false
  }

  private object Handler {

    private def findTypeDefinition(implicit editor: Editor, file: PsiFile) =
      elementOfTypeAtCaret(classOf[ScClass], classOf[ScObject], classOf[ScTrait])

    private def strippedName(definition: ScTypeDefinition) = {
      val name = definition.name
      definition match {
        case _: ScObject => name.stripSuffix("$")
        case _ => name
      }
    }

    /**
      * Get class fields using wizard dialog.
      */
    private def showWizard(definition: ScTypeDefinition)
                          (implicit project: Project): Option[(Seq[ScNamedElement], Boolean)] =
      fields(definition) ++ parameterlessMethods(definition) match {
        case members if ApplicationManager.getApplication.isUnitTestMode => Some((members, true))
        case members =>
          new ui.ScalaGenerateToStringWizard(members) match {
            case wizard if wizard.showAndGet() => Some(wizard.membersWithSelection)
            case _ => None
          }
      }

  }

}

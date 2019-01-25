package org.jetbrains.plugins.scala
package codeInsight
package generation
package actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
  * Generate toString metod action.
  *
  * @author Rado Buransky (buransky.com)
  */
final class ScalaGenerateToStringAction extends ScalaBaseGenerateAction(new ScalaGenerateToStringAction.Handler)

object ScalaGenerateToStringAction {

  private[generation] final class Handler extends ScalaCodeInsightActionHandler {

    import Handler._

    override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
      if (!FileDocumentManager.getInstance.requestWriting(editor.getDocument, project)) return

      findTypeDefinition(editor, file).foreach { definition =>
        val toStringMethod = createToString(definition, project)

        extensions.inWriteAction {
          addMembers(definition, toStringMethod.toList, editor.getDocument)
        }
      }
    }

    /**
      * Determines whether toString can be generated for the class.
      */
    override def isValidFor(editor: Editor, file: PsiFile): Boolean =
      super.isValidFor(editor, file) && findTypeDefinition(editor, file).exists(isTypeDefinitionValid)
  }

  private object Handler {

    private def findTypeDefinition(implicit editor: Editor, file: PsiFile) =
      elementOfTypeAtCaret(classOf[ScClass], classOf[ScObject], classOf[ScTrait])

    private def isTypeDefinitionValid(definition: ScTypeDefinition): Boolean = definition match {
      case _: ScTrait => true
      case _: ScClass | _: ScObject => !definition.isCase
      case _ => false
    }

    /**
      * Create toString method signature.
      */
    private def createToString(definition: ScTypeDefinition, project: Project): Option[ScFunction] = {
      val typeName = definition match {
        case _: ScObject if definition.name.last == '$' => definition.name.dropRight(1)
        case _ => definition.name
      }

      showWizard(definition, project).map { result =>
        val (fields, withFieldNames) = result

        def getDollarName(elem: ScNamedElement) = "$" + elem.name

        val fieldsWithNames = if (withFieldNames)
          fields.map(field => field.name + "=" + getDollarName(field))
        else
          fields.map(getDollarName)

        val fieldsText = fieldsWithNames.mkString(s"$typeName(", ", ", ")")
        val methodText = s"""override def toString = s"$fieldsText""""
        ScalaPsiElementFactory.createMethodWithContext(methodText, definition, definition.extendsBlock)
      }
    }

    /**
      * Get class fields using wizard dialog.
      */
    private def showWizard(definition: ScTypeDefinition, project: Project): Option[(Seq[ScNamedElement], Boolean)] = {
      val allSuitableMembers = getAllSuitableMembers(definition)
      if (ApplicationManager.getApplication.isUnitTestMode) Some(allSuitableMembers, true)
      else
        Some(new ui.ScalaGenerateToStringWizard(project, allSuitableMembers))
          .filter(_.showAndGet())
          .map { wizard =>
            (wizard.getToStringFields, wizard.withFieldNames)
          }
    }

    private def getAllSuitableMembers(definition: ScTypeDefinition): Seq[ScNamedElement] =
      getAllFields(definition) ++ getAllParameterlessMethods(definition)
  }

}

package org.jetbrains.plugins.scala.lang.refactoring.move.anonymousToInner

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.{PsiComment, PsiElement}
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScNewTemplateDefinition, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum, ScObject, ScTemplateDefinition, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.api.{ScFile, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createElementFromText
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ScalaVariableData
import org.jetbrains.plugins.scala.project.ProjectContext

/**
 * @note original implementation was inspired by [[com.intellij.refactoring.anonymousToInner.AnonymousToInnerHandler]]
 */
object ScalaAnonymousToInnerHandler {
  private val LOG = Logger.getInstance(ScalaAnonymousToInnerHandler.getClass)

  def invoke(project: Project, element: ScNewTemplateDefinition): Unit = {
    val extendsBlock = element.extendsBlock
    val (variables, targetContainer) = parseInitialExtendsBlock(extendsBlock)

    for {
      DialogResult(className, renamedVariables) <- showRefactoringDialog(project, extendsBlock, variables, targetContainer)
    } yield performRefactoring(project, className, renamedVariables, extendsBlock, element, targetContainer)
  }

  @VisibleForTesting
  def parseInitialExtendsBlock(extendsBlock: ScExtendsBlock): (Array[ScalaVariableData], Either[ScFile, ScTemplateDefinition]) = {
    val targetContainer = findTargetContainer(extendsBlock)
    val usedVariables = collectUsedVariables(extendsBlock, targetContainer)
    (usedVariables, targetContainer)
  }

  def performRefactoring(project: Project, className: String, variables: Array[ScalaVariableData], anonClass: ScExtendsBlock, originalElement: ScNewTemplateDefinition, targetContainer: Either[ScFile, ScTemplateDefinition]): Unit =
    CommandProcessor.getInstance.executeCommand(project, () => {
      val action: Runnable = () => {
        try {
          val innerClassNewTemplate = newTemplateForInnerClass(project, className, variables, originalElement)
          val (newClassFromAnonymous, maybeWarningComment) = createClass(className, anonClass, variables, project)
          val containerWithNewInnerClass = targetContainer
            .fold(file => file, classOrObject => classOrObject)
            .add(newClassFromAnonymous)

          maybeWarningComment.foreach(comment => containerWithNewInnerClass.addBefore(comment, newClassFromAnonymous))
          originalElement.replace(innerClassNewTemplate)
        }
        catch {
          case e: IncorrectOperationException =>
            LOG.error(e)
        }

      }
      ApplicationManager.getApplication.runWriteAction(action)

    }, getRefactoringName, null)

  private def newTemplateForInnerClass(project: Project, name: String, variables: Array[ScalaVariableData], newTemplate: ScNewTemplateDefinition) = {
    implicit val projectContext: ProjectContext = new ProjectContext(project)

    val args = variables.map(_.variable.getName).mkString(", ")
    val text = s"""new $name($args)""".stripMargin

    createElementFromText[ScNewTemplateDefinition](text, newTemplate)
  }

  private def collectUsedVariables(anonClass: ScExtendsBlock, targetContainer: Either[ScFile, ScTemplateDefinition]): Array[ScalaVariableData] = {
    var res: List[ScTypedDefinition] = Nil
    anonClass.accept(new ScalaRecursiveElementVisitor() {
      override def visitReferenceExpression(expression: ScReferenceExpression) = {
        val refElement = expression.resolve
        refElement match {
          case p: ScParameter =>
            val containingClass = p.getDeclarationScope
            if (targetContainer.fold(_ => false, scTemplateDefinition => containingClass != scTemplateDefinition && !containingClass.isAncestorOf(scTemplateDefinition)))
              res = p :: res
          case r: ScReferencePattern =>
            val containingClass = r.containingClass
            if (targetContainer.fold(_ => false, scTemplateDefinition => containingClass != scTemplateDefinition && !containingClass.isAncestorOf(scTemplateDefinition) && !findTargetContainer(r).isLeft))
              res = r :: res
          case _ =>
        }
        super.visitReferenceExpression(expression)
      }
    })
    res.reverse.distinct.map(parameter =>
      new ScalaVariableData(parameter, true, parameter.`type`().getOrNothing)
    ).toArray

  }

  private case class DialogResult(className: String, variables: Array[ScalaVariableData])

  private def showRefactoringDialog(project: Project, extendsBlock: ScExtendsBlock, usedVariables: Array[ScalaVariableData], target: Either[ScFile, ScTemplateDefinition]) = {
    val dialog = new ScalaAnonymousToInnerDialog(project, extendsBlock, usedVariables, target)
    if (dialog.showAndGet())
      Some(DialogResult(dialog.getClassName, dialog.getVariables))
    else
      None
  }

  private def createClass(name: String, anonClass: ScExtendsBlock, variables: Array[ScalaVariableData], project: Project): (ScClass, Option[PsiComment]) = {
    implicit val projectContext: ProjectContext = new ProjectContext(project)

    val containsVars = variables.exists(_.element.isVar)
    val warningComment = if (containsVars)
      Some(createElementFromText[PsiComment]("// refactor your code or use scala.runtime.ObjectRef", anonClass))
    else
      None

    val parameters = variables.map(v => s"${v.name}: ${v.`type`.getPresentableText}").mkString(", ")
    val text = s"class $name($parameters) extends ${anonClass.getText}"

    val newClass = createElementFromText[ScClass](text, anonClass)

    val parameterOldNameToNewName = variables.map(v => v.variable.getName -> v.name).toMap
    newClass.accept(new ScalaRecursiveElementVisitor() {
      override def visitReferenceExpression(expression: ScReferenceExpression) = {
        val refElement = expression.resolve
        refElement match {
          case p: ScParameter =>
            parameterOldNameToNewName.get(p.getName).foreach(expression.handleElementRename)
          case r: ScReferencePattern =>
            parameterOldNameToNewName.get(r.getName).foreach(expression.handleElementRename)
          case _ =>
        }
        super.visitReferenceExpression(expression)
      }
    })

    (newClass, warningComment)
  }

  private def findTargetContainer(elem: PsiElement): Either[ScFile, ScTemplateDefinition] = {
    val container = elem.parents.collectFirst {
      case obj: ScObject => Right(obj)
      case enm: ScEnum => Right(enm)
      case clazz: ScClass => Right(clazz)
      case trt: ScTrait => Right(trt)
      case file: ScFile => Left(file) //Scala3 top level definition
    }
    container.get //assuming that there is at least "ScFile" parent, so it's safe to call "get"
  }

  @NlsContexts.DialogTitle def getRefactoringName = ScalaBundle.message("move.anonymousToInner.name")
}

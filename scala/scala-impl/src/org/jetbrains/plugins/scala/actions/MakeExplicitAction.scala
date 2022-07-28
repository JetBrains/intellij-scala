package org.jetbrains.plugins.scala
package actions

import com.intellij.openapi.actionSystem._
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.ui.popup.{JBPopup, JBPopupFactory, PopupChooserBuilder, PopupStep}
import com.intellij.psi.util.PsiUtilBase
import com.intellij.psi.{NavigatablePsiElement, PsiDocumentManager, PsiElement, PsiNamedElement}
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

import java.awt.Point
import javax.swing.JList

final class MakeExplicitAction extends AnAction(
  ScalaBundle.message("make.implicit.conversion.explicit.action.text"),
  ScalaBundle.message("make.implicit.conversion.explicit.action.description"),
  /* icon = */ null
) {

  override def actionPerformed(event: AnActionEvent): Unit = {
    val context = event.getDataContext
    val project = CommonDataKeys.PROJECT.getData(context) match {
      case null => return
      case value => value
    }

    PlatformCoreDataKeys.SELECTED_ITEM.getData(context) match {
      case Parameters(function: ScFunction, oldExpression, _, editor, elements) if
      oldExpression != null &&
        editor != null &&
        elements != null =>
        PsiUtilBase.getPsiFileInEditor(editor, project) match {
          case _: ScalaFile => MakeExplicitAction.showMakeExplicitPopup(oldExpression, function, elements)(project, editor)
          case _ =>
        }
      case _ =>
    }
  }
}

object MakeExplicitAction {

  import JBPopupFactory.{getInstance => PopupFactory}
  import ScalaPsiElementFactory.{createExpressionFromText, createReferenceFromText}

  private val MakeExplicit = ScalaBundle.message("make.explicit")
  private val MakeExplicitStatically = ScalaBundle.message("make.explicit.and.import.method")

  private[this] var popup: JBPopup = _

  def createPopup(list: JList[Parameters]): JBPopup = {
    GoToImplicitConversionAction.setList(list)
    popup = createPopupBuilder(list).createPopup
    popup
  }

  private[this] def createPopupBuilder(list: JList[Parameters]) =
    new PopupChooserBuilder(list)
      .setTitle(ScalaBundle.message("title.choose.implicit.conversion.method"))
      .setAdText(ScalaBundle.message("press.alt.enter"))
      .setMovable(false)
      .setResizable(false)
      .setRequestFocus(true)
      .setItemChoosenCallback(() => list.getSelectedValue match {
        case Parameters(navigable: NavigatablePsiElement, _, _, _, _) =>
          val maybeSynthetic = navigable match {
            case function: ScFunction =>
              function.syntheticNavigationElement match {
                case synthetic: NavigatablePsiElement => Some(synthetic)
                case _ => None
              }
            case _ => None
          }

          maybeSynthetic.getOrElse(navigable).navigate(true)
        case _ =>
      })

  def showMakeExplicitPopup(expression: ScExpression, function: ScFunction,
                            elements: Seq[PsiNamedElement])
                           (implicit project: Project, editor: Editor): Unit = {
    val step = new ActionPopupStep(expression, function, elements.contains(function))
    val list = GoToImplicitConversionAction.getList

    PopupFactory.createListPopup(step)
      .show(new RelativePoint(list, currentItemPoint(list)))
  }

  def currentItemPoint(list: JList[_], moveLeft: Int = 20): Point = list.getSelectedIndex match {
    case -1 => throw new RuntimeException("Index = -1 is less than zero.")
    case index =>
      list.getCellBounds(index, index) match {
        case null => throw new RuntimeException(s"No bounds for index = $index.")
        case bounds => new Point(bounds.x + bounds.width - moveLeft, bounds.y)
      }
  }

  private class ActionPopupStep(expression: ScExpression, function: ScFunction,
                                importStatically: Boolean)
                               (implicit project: Project, editor: Editor)
    extends BaseListPopupStep[String](null,
      (if (importStatically) Array(MakeExplicit, MakeExplicitStatically) else Array(MakeExplicit)): _*) {

    override def getTextFor(value: String): String = value

    override def onChosen(selectedValue: String, finalChoice: Boolean): PopupStep[_] =
      selectedValue match {
        case null =>
          PopupStep.FINAL_CHOICE
        case value if finalChoice =>
          PsiDocumentManager.getInstance(project).commitAllDocuments()
          popup.dispose()

          value match {
            case MakeExplicit => replaceWithExplicit(expression, function, importStatically)(project, editor)
            case MakeExplicitStatically => replaceWithExplicitStatically()
          }

          PopupStep.FINAL_CHOICE
        case _ =>
          super.onChosen(selectedValue, finalChoice)
      }

    private def replaceWithExplicitStatically(): Unit = {
      val replacementText = methodCallText(expression, function)
      val (maybeClass, prefix) = classAndPrefix(function, importStatically)(_.qualifiedName)

      runReplace(expression, replacementText)(Option(createReferenceFromText(prefix + replacementText).resolve())) {
        case (reference: ScReferenceExpression, target) => reference.bindToElement(target, maybeClass)
      }
    }
  }

  def replaceWithExplicit(expression: ScExpression, function: ScFunction,
                          importStatically: Boolean)
                         (implicit project: Project, editor: Editor): Unit = {
    val (maybeClass, prefix) = classAndPrefix(function, importStatically)(_.name)

    runReplace(expression, prefix + methodCallText(expression, function))(maybeClass) {
      case (ScReferenceExpression.withQualifier(reference: ScReferenceExpression), clazz) => reference.bindToElement(clazz)
    }
  }

  private def methodCallText(expression: ScExpression, function: ScFunction) =
    s"${function.name}(${expression.getText})"

  private[this] def classAndPrefix(function: ScFunction, importStatically: Boolean)
                                  (className: ScTemplateDefinition => String) = {
    val maybeClass = if (importStatically) Option(function.containingClass) else None
    (maybeClass, maybeClass.fold("")(className.andThen(_ + ".")))
  }

  private[this] def runReplace(expression: ScExpression, replacementText: String)
                              (findTarget: => Option[PsiElement])
                              (onExpresion: PartialFunction[(ScExpression, PsiElement), Unit])
                              (implicit project: Project, editor: Editor): Unit = startCommand() {
    val replacement = createExpressionFromText(replacementText)
    inWriteAction {
      val methodCall = expression.replace(replacement).asInstanceOf[ScMethodCall]
      for {
        target <- findTarget
      } onExpresion(methodCall.deepestInvokedExpr, target)

      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }
}

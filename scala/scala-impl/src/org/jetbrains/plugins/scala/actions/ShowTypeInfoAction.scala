package org.jetbrains.plugins.scala.actions

import _root_.com.intellij.codeInsight.TargetElementUtil
import _root_.com.intellij.psi._
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.application.{NonBlockingReadAction, ReadAction}
import com.intellij.openapi.editor.{Editor, SelectionModel}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.{PsiTreeUtil, PsiUtilBase}
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.plugins.scala.actions.ShowTypeInfoAction.typeTextOf
import org.jetbrains.plugins.scala.actions.utils.TaskRunnerWithLoadingProgress
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScUnderscoreSection}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScType, ScTypeExt, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.getSelectedExpression
import org.jetbrains.plugins.scala.statistics.ScalaActionUsagesCollector
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaLanguage}

import java.util.concurrent.Callable
import java.util.function.Consumer

/**
 * @todo ideally we should not create our custom action
 *       and rely on [[com.intellij.codeInsight.hint.actions.ShowExpressionTypeAction]]<br>
 *       by implementing [[com.intellij.lang.ExpressionTypeProvider]]
 *       There was an attempt to do it in 2018 [[https://youtrack.jetbrains.com/issue/SCL-14464]]
 *       but later the change was reverted for some reasons (see the comments in the YT ticket)
 *       We might give it another go, we need to review the latest state of the feature in platform to see if it satisfies our needs
 */
class ShowTypeInfoAction extends AnAction(
  ScalaBundle.message("type.info.text"),
  ScalaBundle.message("type.info.description"),
  /* icon = */ null
) {

  override def update(e: AnActionEvent): Unit =
    ScalaActionUtil.enableAndShowIfInScalaFile(e)

  override def getActionUpdateThread: ActionUpdateThread =
    ActionUpdateThread.BGT

  override def actionPerformed(e: AnActionEvent): Unit = {
    val context = e.getDataContext
    val editor: Editor = CommonDataKeys.EDITOR.getData(context)
    if (editor == null)
      return
    val file = PsiUtilBase.getPsiFileInEditor(editor, CommonDataKeys.PROJECT.getData(context))
    if (file == null)
      return
    if (!file.getLanguage.isKindOf(ScalaLanguage.INSTANCE))
      return

    val project = file.getProject
    ScalaActionUsagesCollector.logTypeInfo(project)
    invokeAction(editor, file, project)
  }

  private def invokeAction(editor: Editor, file: PsiFile, project: Project): Unit = {
    val selectionModel = editor.getSelectionModel

    @RequiresBackgroundThread
    def calculateTypeInfo(): Option[String] = {
      if (selectionModel.hasSelection) {
        getTypeInfoHintForSelection(editor, file, project, selectionModel)
      } else {
        // Not 100% sure what this offset adjusting is doing
        val offset = editor.logicalPositionToOffset(editor.getCaretModel.getLogicalPosition)
        val offsetAdjusted = TargetElementUtil.adjustOffset(file, editor.getDocument, offset)
        ShowTypeInfoAction.getTypeInfoHint(file, offsetAdjusted)
      }
    }

    TaskRunnerWithLoadingProgress.runSingleInstanceActionTask[Option[String]](
      project = project,
      backgroundDataSupplier = () => {
        calculateTypeInfo()
      },
      uiDataConsumer = { hintOption =>
        hintOption.foreach(ScalaActionUtil.showHint(editor, _))
      },
      progressTitle = ScalaBundle.message("calculating.type.info"),
      editor = editor,
      // I decided not to cancel the tooltip on scrolling - if it takes long to compute the types in complex code bases,
      // it can be annoying that you can't even scroll the file... On the other hand, the final tooltip with the type hint
      // will be hidden once you scroll, so the behavior is not 100% consistent =/
      cancelOnScrolling = false,
      originalAction = this
    )
  }

  private def getTypeInfoHintForSelection(editor: Editor, file: PsiFile, project: Project, selectionModel: SelectionModel): Option[String] = {
    val start = selectionModel.getSelectionStart
    val end = selectionModel.getSelectionEnd

    def hintForPattern: Option[String] = {
      val pattern = Option(PsiTreeUtil.findElementOfClassAtRange(file, start, end, classOf[ScBindingPattern]))
        .orElse(Option(PsiTreeUtil.findElementOfClassAtRange(file, start, end, classOf[ScWildcardPattern])))
      pattern.flatMap { p =>
        implicit val tpc: TypePresentationContext = TypePresentationContext(p)
        implicit val context: Context = Context(p)

        typeTextOf(p, ScSubstitutor.empty).map("Type: " + _)
      }
    }

    def hintForExpression: Option[String] = {
      val selectedExpression = getSelectedExpression(file)(project, editor)
      selectedExpression.map {
        case expr@Typeable(tpe) =>
          expressionTypeHintForSelection(expr, tpe)
        case _ =>
          ScalaBundle.message("could.not.find.type.for.selection")
      }
    }

    def hintForParameter: Option[String] = {
      val parameter = PsiTreeUtil.findElementOfClassAtRange(file, start, end, classOf[ScParameter])
      if (parameter == null) None
      else {
        implicit val tpc: TypePresentationContext = parameter
        implicit val context: Context = Context(parameter)

        val scType = parameter.typeOfNamedElement(ScSubstitutor.empty)
        scType.map(_.presentableText)
      }
    }

    val hint = hintForPattern
      .orElse(hintForExpression)
      .orElse(hintForParameter)
    hint.map(StringUtil.escapeXmlEntities)
  }

  private def expressionTypeHintForSelection(expr: ScExpression, tpe: ScType): String = {
    implicit val tpc: TypePresentationContext = expr
    implicit val context: Context = Context(expr)

    val tpeText = tpe.presentableText
    val withoutAliases = Some(TypePresentation.withoutAliases(tpe))
    val tpeWithoutImplicits = expr.getTypeWithoutImplicits().toOption
    val tpeWithoutImplicitsText = tpeWithoutImplicits.map(_.presentableText)
    val expectedTypeText = expr.expectedType().map(_.presentableText)
    val nonSingletonTypeText = tpe.extractDesignatorSingleton.map(_.presentableText)

    val mainText = Seq("Type: " + tpeText)

    def additionalTypeText(typeText: Option[String], label: String) = typeText.filter(_ != tpeText).map(s"$label: " + _)

    val nonSingleton = additionalTypeText(nonSingletonTypeText, ScalaBundle.message("hint.label.non.singleton"))
    val simplified = additionalTypeText(withoutAliases, ScalaBundle.message("hint.label.simplified"))
    val orig = additionalTypeText(tpeWithoutImplicitsText, ScalaBundle.message("hint.label.original"))
    val expected = additionalTypeText(expectedTypeText, ScalaBundle.message("hint.label.expected"))
    val types = mainText ++ simplified.orElse(nonSingleton) ++ orig ++ expected

    if (types.size == 1) tpeText
    else types.mkString("\n")
  }
}

object ShowTypeInfoAction {
  val ActionId: String = "Scala.TypeInfo"

  private def getTypeInfoHint(file: PsiFile, offset: Int): Option[String] = {
    val typeInfoFromRef = file.findReferenceAt(offset) match {
      case ref @ ResolvedWithSubst(e, subst) =>
        implicit val tpc: TypePresentationContext = TypePresentationContext(ref.getElement)
        implicit val context: Context = Context(ref.getElement)

        typeTextOf(e, subst)
      case _ =>
        val element = file.findElementAt(offset)
        if (element == null) return None

        implicit val tpc: TypePresentationContext = TypePresentationContext(element)
        implicit val context: Context = Context(element)

        element.elementType match {
          case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER =>
          case _ =>
            return None
        }

        element match {
          case Parent(p) => typeTextOf(p, ScSubstitutor.empty)
          case _         => None
        }
    }

    typeInfoFromRef.orElse {
      val pattern = PsiTreeUtil.findElementOfClassAtOffset(file, offset, classOf[ScBindingPattern], false)
      if (pattern != null) {
        implicit val tpc: TypePresentationContext = TypePresentationContext(pattern)
        implicit val context: Context = Context(pattern)

        typeTextOf(pattern, ScSubstitutor.empty)
      } else {
        None
      }
    }
  }

  private def typeTextOf(elem: PsiElement, subst: ScSubstitutor)
                        (implicit tpc: TypePresentationContext, context: Context): Option[String] = {
    val scType = elem.typeOfNamedElement(subst).orElse {
      elem match {
        case under: ScUnderscoreSection => under.`type`().toOption
        case under: ScWildcardPattern => under.`type`().toOption
        case _ => None
      }
    }
    scType.map(TypePresentation.withoutAliases)
  }

  private[this] def typeText(optType: Option[ScType])
                            (implicit tpc: TypePresentationContext, context: Context): Option[String] =
    optType.map(TypePresentation.withoutAliases)
}

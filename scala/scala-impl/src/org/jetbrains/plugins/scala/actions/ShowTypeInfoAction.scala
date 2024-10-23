package org.jetbrains.plugins.scala.actions

import _root_.com.intellij.codeInsight.TargetElementUtil
import _root_.com.intellij.psi._
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.{PsiTreeUtil, PsiUtilBase}
import org.jetbrains.plugins.scala.actions.ShowTypeInfoAction.typeTextOf
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScUnderscoreSection
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.getSelectedExpression
import org.jetbrains.plugins.scala.statistics.ScalaActionUsagesCollector
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaLanguage}

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
    implicit val editor: Editor = CommonDataKeys.EDITOR.getData(context)
    if (editor == null) return

    val file = PsiUtilBase.getPsiFileInEditor(editor, CommonDataKeys.PROJECT.getData(context))
    if (file == null || !file.getLanguage.isKindOf(ScalaLanguage.INSTANCE)) return

    ScalaActionUsagesCollector.logTypeInfo(file.getProject)

    val selectionModel = editor.getSelectionModel
    if (selectionModel.hasSelection) {
      val start = selectionModel.getSelectionStart
      val end = selectionModel.getSelectionEnd

      def hintForPattern: Option[String] = {
        val pattern = Option(PsiTreeUtil.findElementOfClassAtRange(file, start, end, classOf[ScBindingPattern]))
          .orElse(Option(PsiTreeUtil.findElementOfClassAtRange(file, start, end, classOf[ScWildcardPattern])))
        pattern.flatMap(p => typeTextOf(p, ScSubstitutor.empty)(p).map("Type: " + _))
      }

      implicit val project: Project = file.getProject

      def hintForExpression: Option[String] = {
        getSelectedExpression(file).map {
          case expr@Typeable(tpe) =>
            implicit val context: TypePresentationContext = expr
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
          case _ => ScalaBundle.message("could.not.find.type.for.selection")
        }
      }

      def hintForParameter: Option[String] = {
        val parameter = PsiTreeUtil.findElementOfClassAtRange(file, start, end, classOf[ScParameter])
        if (parameter == null) None
        else {
          val scType = parameter.ofNamedElement(ScSubstitutor.empty)
          scType.map(_.presentableText(parameter: TypePresentationContext))
        }
      }

      val hint = hintForPattern
        .orElse(hintForExpression)
        .orElse(hintForParameter)
      val hintEscaped = hint.map(StringUtil.escapeXmlEntities)
      hintEscaped.foreach(ScalaActionUtil.showHint(editor, _))
    } else {
      val offset = TargetElementUtil.adjustOffset(file, editor.getDocument,
        editor.logicalPositionToOffset(editor.getCaretModel.getLogicalPosition))

      ShowTypeInfoAction.getTypeInfoHint(file, offset).foreach(ScalaActionUtil.showHint(editor, _))
    }
  }
}

object ShowTypeInfoAction {
  val ActionId: String = "Scala.TypeInfo"

  def getTypeInfoHint(file: PsiFile, offset: Int): Option[String] = {
    val typeInfoFromRef = file.findReferenceAt(offset) match {
      case ref @ ResolvedWithSubst(e, subst) => typeTextOf(e, subst)(ref.getElement)
      case _ =>
        val element = file.findElementAt(offset)
        if (element == null) return None

        element.elementType match {
          case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER =>
          case _ =>
            return None
        }

        element match {
          case Parent(p) => typeTextOf(p, ScSubstitutor.empty)(element)
          case _         => None
        }
    }

    typeInfoFromRef.orElse {
      val pattern = PsiTreeUtil.findElementOfClassAtOffset(file, offset, classOf[ScBindingPattern], false)
      if (pattern != null)
        typeTextOf(pattern, ScSubstitutor.empty)(pattern)
      else
        None
    }
  }

  private def typeTextOf(elem: PsiElement, subst: ScSubstitutor)
                              (implicit context: TypePresentationContext): Option[String] = {
    val scType = elem.ofNamedElement(subst).orElse {
      elem match {
        case under: ScUnderscoreSection => under.`type`().toOption
        case under: ScWildcardPattern => under.`type`().toOption
        case _ => None
      }
    }
    scType.map(TypePresentation.withoutAliases)
  }

  private[this] def typeText(optType: Option[ScType])
                            (implicit context: TypePresentationContext): Option[String] =
    optType.map(TypePresentation.withoutAliases)
}

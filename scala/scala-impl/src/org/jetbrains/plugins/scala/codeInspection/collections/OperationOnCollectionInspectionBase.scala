package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.codeInspection.options.{OptPane, OptionController}
import com.intellij.codeInspection.ui.StringValidatorWithSwingSelector
import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import one.util.streamex.StreamEx
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionInspectionBase._
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.settings.{ScalaApplicationSettings, ScalaProjectSettingsUtil}

import java.util.function.{Consumer, Supplier}
import java.{util => ju}
import scala.collection.immutable.ArraySeq

object OperationOnCollectionInspectionBase {
  val inspectionId: String = ScalaInspectionBundle.message("operation.on.collection.id")
  val inspectionName: String = ScalaInspectionBundle.message("operation.on.collection.name")

  val likeOptionClassesDefault: Array[String] = Array("scala.Option", "scala.Some", "scala.None")
  val likeCollectionClassesDefault: Array[String] = Array("scala.collection._", "scala.Array", "scala.Option", "scala.Some", "scala.None", "java.lang.String")

  private val likeOptionKey = "operation.on.collection.like.option"
  private val likeCollectionKey = "operation.on.collection.like.collection"

  private val simplificationTypesPrefix = "operation.on.collection.simplification.type"

  private val inputMessages = Map(
    likeCollectionKey -> ScalaInspectionBundle.message("operation.on.collection.like.collection.input.message"),
    likeOptionKey -> ScalaInspectionBundle.message("operation.on.collection.like.option.input.message")
  )

  private val inputTitles = Map(
    likeCollectionKey -> ScalaInspectionBundle.message("operation.on.collection.like.collection.input.title"),
    likeOptionKey -> ScalaInspectionBundle.message("operation.on.collection.like.option.input.title")
  )

  private val panelTitles = Map(
    likeCollectionKey -> ScalaInspectionBundle.message("operation.on.collection.like.collection.panel.title"),
    likeOptionKey -> ScalaInspectionBundle.message("operation.on.collection.like.option.panel.title")
  )

  object SimplifiableExpression {
    def unapply(expr: ScExpression): Option[ScExpression] =
      if (expr.is[ScBlock, ScParenthesisedExpr]) None
      else Some(expr)
  }

  private class PatternStringValidator(@Nls inputTitle: String, @Nls inputMessage: String) extends StringValidatorWithSwingSelector {
    override def validatorId(): String = "scala.pattern.validator"

    override def select(project: Project): String =
      Messages.showInputDialog(
        project,
        inputMessage,
        inputTitle,
        Messages.getWarningIcon,
        "",
        ScalaProjectSettingsUtil.getPatternValidator
      )

    // TODO: Basically, the same as ScalaProjectSettingsUtil.getPatternValidator#checkInput
    //       but return nullable error message instead of Boolean
    override def getErrorMessage(project: Project, string: String): String = null
  }
}

abstract class OperationOnCollectionInspectionBase extends LocalInspectionTool {
  private val settings = ScalaApplicationSettings.getInstance()

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case SimplifiableExpression(expr) => simplifications(expr).foreach {
      case s@Simplification(toReplace, _, hint, rangeInParent) =>
        val quickFix = OperationOnCollectionQuickFix(s)
        holder.registerProblem(toReplace.getElement, hint, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, rangeInParent, quickFix)
    }
    case _ =>
  }

  private def simplifications(expr: ScExpression): Seq[Simplification] = {
    def simplificationTypes = for {
      (st, idx) <- possibleSimplificationTypes.zipWithIndex
      if getSimplificationTypesEnabled(idx)
    } yield st

    simplificationTypes.flatMap(st => st.getSimplifications(expr) ++ st.getSimplification(expr))
  }

  def getLikeCollectionClasses: Seq[String] = ArraySeq.unsafeWrapArray(settings.getLikeCollectionClasses)
  def getLikeOptionClasses: Seq[String] = ArraySeq.unsafeWrapArray(settings.getLikeOptionClasses)
  def setLikeCollectionClasses(values: Seq[String]): Unit = settings.setLikeCollectionClasses(values.toArray)
  def setLikeOptionClasses(values: Seq[String]): Unit = settings.setLikeOptionClasses(values.toArray)

  def possibleSimplificationTypes: Seq[SimplificationType]
  def getSimplificationTypesEnabled: Array[java.lang.Boolean]
  def setSimplificationTypesEnabled(values: Array[java.lang.Boolean]): Unit

  private val patternLists = Map(
    likeCollectionKey -> (() => getLikeCollectionClasses),
    likeOptionKey -> (() => getLikeOptionClasses)
  )

  private val setPatternLists = {
    Map(
      likeCollectionKey -> setLikeCollectionClasses _,
      likeOptionKey -> setLikeOptionClasses _
    )
  }

  override def getOptionsPane: OptPane = {
    def patternList(patternListKey: String) = OptPane.stringList(
      patternListKey,
      panelTitles(patternListKey),
      new PatternStringValidator(
        inputTitles(patternListKey),
        inputMessages(patternListKey)
      )
    )

    val patternsPanel = OptPane.horizontalStack(patternList(likeCollectionKey), patternList(likeOptionKey))

    if (possibleSimplificationTypes.sizeIs > 1) {
      val checkboxes = possibleSimplificationTypes.zipWithIndex.map { case (t, idx) =>
        OptPane.checkbox(
          idx.toString,
          t.description
        ).prefix(simplificationTypesPrefix)
      }

      OptPane.pane(OptPane.checkboxPanel(checkboxes: _*), patternsPanel)
    } else OptPane.pane(patternsPanel)
  }

  // TODO: this way changes to likeCollection/likeOption lists are not detected by Swing
  //       and 'Apply'/'Reset' buttons are not activated (worked like that before refactoring)
  //       probably caused by PersistentStateComponent usage
  override def getOptionController: OptionController =
    super.getOptionController
      .onValue(likeCollectionKey, getMutablePatternList(likeCollectionKey), consumeNewPatternList(likeCollectionKey))
      .onValue(likeOptionKey, getMutablePatternList(likeOptionKey), consumeNewPatternList(likeOptionKey))
      .onPrefix(
        simplificationTypesPrefix,
        (idx: String) => getSimplificationTypesEnabled(idx.toInt),
        (idx: String, enabled: Any) =>
          setSimplificationTypesEnabled(
            getSimplificationTypesEnabled.updated(idx.toInt, enabled.asInstanceOf[Boolean])
          )
      )

  private def getMutablePatternList(patternListKey: String): Supplier[ju.List[String]] =
    () => StreamEx.of(patternLists(patternListKey)(): _*).toMutableList

  private def consumeNewPatternList(patternListKey: String): Consumer[ju.List[String]] = { newList =>
    val newArray = newList.toArray.collect { case s: String => s }
    setPatternLists(patternListKey)(ArraySeq.unsafeWrapArray(newArray))
  }
}

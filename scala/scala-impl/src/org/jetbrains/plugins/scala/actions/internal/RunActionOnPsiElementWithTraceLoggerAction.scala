package org.jetbrains.plugins.scala.actions.internal

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiUtilBase
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import org.jetbrains.plugins.scala.actions.internal.RunActionOnPsiElementWithTraceLoggerAction._
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, StringExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.stubs.ScImplicitStub
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.traceLogger.ToData.{Raw => RawData}
import org.jetbrains.plugins.scala.traceLogger.{ToData, TraceLogger}

import scala.collection.immutable.ArraySeq

//noinspection ScalaExtractStringToBundle
class RunActionOnPsiElementWithTraceLoggerAction extends AnAction(
  "Run action on PsiElement with TraceLogger",
  "Run an action on a PsiElement while logging with TraceLogger",
  /* icon = */ null
){
  override def update(e: AnActionEvent): Unit = ScalaActionUtil.enableAndShowIfInScalaFile(e)

  override def actionPerformed(e: AnActionEvent): Unit = {
    val context = e.getDataContext
    implicit val project: Project = CommonDataKeys.PROJECT.getData(context)
    implicit val editor: Editor = CommonDataKeys.EDITOR.getData(context)
    if (project == null || editor == null)
      return

    val file = PsiUtilBase.getPsiFileInEditor(editor, project)
    val actions = findActionsForSelection(file)

    actions match {
      case Seq() => ScalaActionUtil.showHint(editor, "No actions for PsiElements")
      case Seq(action) => run(action)
      case actions =>
        ScalaRefactoringUtil.showChooserGeneric[Action](
          editor, actions, run(_), "Choose action", _.presentation, _.element
        )
    }
  }

  private def run(action: Action)(implicit project: Project): Unit = {
    TraceLogger.runWithTraceLogger(action.id) {
      TraceLogger.log(s"Log: ${action.presentation}")
      TraceLogger.log("Clear all caches...")
      ScalaPsiManager.instance(project).clearAllCachesAndWait()
      TraceLogger.block("block test") {
        TraceLogger.log("some log msg")
      }
      TraceLogger.block("nothing in there") {

      }
      val result = action.run()
      TraceLogger.log("Done.", result)
    }
  }

  private def findActionsForSelection(file: PsiFile)(implicit editor: Editor): Seq[Action] = {
    val document = editor.getDocument
    val sm = editor.getSelectionModel
    val start = sm.getSelectionStart
    val end = sm.getSelectionEnd
    val startLine = document.getLineNumber(start)
    val endLine = document.getLineNumber(end)
    val selection = TextRange.create(start, end)
    file.findElementAt(selection.getStartOffset).toOption.toSeq.flatMap(
      _.withParentsInFile
        .takeWhile(_.getTextRange.intersects(selection))
        .takeWhile(e =>
          startLine == document.getLineNumber(e.startOffset) ||
            endLine == document.getLineNumber(e.endOffset)
        )
        .filter(e =>
          startLine == document.getLineNumber(e.startOffset) &&
            endLine == document.getLineNumber(e.endOffset)
        )
        .flatMap(actionsFor)
    )
  }

  private def actionsFor(e: PsiElement): Seq[Action] =
    actionProviders.flatMap(_.lift(e))
}

object RunActionOnPsiElementWithTraceLoggerAction {
  trait Action {
    def element: PsiElement
    def run(): RawData
    def id: String
    def presentation: String
  }

  case class ActionImpl[E <: PsiElement, R: ToData](name: String, override val element: E)(actualRun: E => R) extends Action {
    private val pointer = element.createSmartPointer
    override def run(): RawData = {
      pointer.getElement match {
        case null => throw new Exception("Original element got invalid")
        case e => ToData.raw(actualRun(e))
      }
    }

    override def id: String = s"${name.replaceAll(" ", "-")}-$element"

    override val presentation: String = {
      s"$name ${element.getText.shorten(30)}"
    }
  }

  type ActionProvider = PartialFunction[PsiElement, Action]

  val actionProviders: Seq[ActionProvider] = ArraySeq(
    typeableActionProvider,
    resolveActionProvider,
  )

  def typeableActionProvider: ActionProvider = {
    case typeable: PsiElement with Typeable => ActionImpl("Infer type of", typeable)(_.`type`())
  }

  def resolveActionProvider: ActionProvider = {
    case reference: ScReference => ActionImpl("Resolve reference", reference) { reference =>
      reference.multiResolveScala(false) match {
        case Array() =>
          TraceLogger.log("Didn't find any resolve results, so try to resolve incomplete")
          reference.multiResolveScala(true)
        case results =>
          results
      }
    }
  }
}

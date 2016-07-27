package org.jetbrains.plugins.scala.annotator.quickfix

import javax.swing.Icon

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.Task.WithResult
import com.intellij.openapi.progress._
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup._
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.{PsiFile, PsiNamedElement}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
import org.jetbrains.plugins.scala.annotator.quickfix.SearchForImplicitClassAction.{ImplicitSearchResult, Name}
import org.jetbrains.plugins.scala.extensions.{inReadAction, inWriteCommandAction}
import org.jetbrains.plugins.scala.lang.completion.ScalaGlobalMembersCompletionContributor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible.ImplicitMapResult
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}

/**
  * Created by Svyatoslav Ilinskiy on 25.07.16.
  */
class SearchForImplicitClassAction(ref: ScReferenceExpression, qualifier: ScExpression) extends IntentionAction {
  override def getFamilyName: String = Name

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    val findImplicits = new WithResult[Seq[ImplicitSearchResult], RuntimeException](project, ScalaBundle.message("searching.for.implicit.classes"), true) {
      override def compute(indicator: ProgressIndicator): Seq[ImplicitSearchResult] = {
        indicator.setIndeterminate(true)
        inReadAction {
          SearchForImplicitClassAction.findImplicitConversion(ref, qualifier)
        }
      }
    }
    val progressManager = ProgressManager.getInstance()
    val funs = progressManager.run(findImplicits)
    if (funs == null) return
    val step: BaseListPopupStep[ImplicitSearchResult] = new BaseListPopupStep(getText, funs: _*) {
      override def getIconFor(value: ImplicitSearchResult): Icon = value.fun.getIcon(0)

      override def getTextFor(value: ImplicitSearchResult): String = {
        val fun = value.fun
        val cl = fun.containingClass
        s"${cl.name}.${fun.name} (${cl.qualifiedName})"
      }

      override def onChosen(value: ImplicitSearchResult, finalChoice: Boolean): PopupStep[_] = {
        val holder = ScalaImportTypeFix.getImportHolder(ref, ref.getProject)
        inWriteCommandAction(project, "Add import for implicit class") {
          holder.addImportForPsiNamedElement(value.elementToImport, ref)
        }
        super.onChosen(value, finalChoice)
      }
    }
    val popup = JBPopupFactory.getInstance().createListPopup(step)
    popup.showInBestPositionFor(editor)
  }

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    file.isInstanceOf[ScalaFile] && ref.isValid
  }

  override def getText: String = getFamilyName

  override def startInWriteAction(): Boolean = false
}

object SearchForImplicitClassAction {
  val Name = ScalaBundle.message("search.for.implicit.class")

  def findImplicitConversion(ref: ScReferenceExpression, qualifier: ScExpression): Seq[ImplicitSearchResult] = {
    implicit val typeSystem = ref.typeSystem
    val tp = qualifier.getType().getOrNothing
    val conversions = ScalaGlobalMembersCompletionContributor.findImplicitConversions(ref, ref.getContainingFile, tp)
    conversions.toSeq.flatMap {
      case ImplicitMapResult(true, resolveResult, _, retTp, _, _, _) =>
        val c = new CompletionProcessor(StdKinds.methodRef, ref)
        c.processType(retTp, ref)
        c.candidates.collect {
          case ScalaResolveResult(fun: ScFunction, _) if fun.name == ref.nameId.getText =>
            ImplicitSearchResult(fun, resolveResult.getActualElement)
        }
      case _ => Seq.empty
    }
  }

  case class ImplicitSearchResult(fun: ScFunction, elementToImport: PsiNamedElement)
}

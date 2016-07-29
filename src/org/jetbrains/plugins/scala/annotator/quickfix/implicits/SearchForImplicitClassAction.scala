package org.jetbrains.plugins.scala.annotator.quickfix.implicits

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiFile, PsiNamedElement}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.quickfix.implicits.SearchForImplicitClassAction.{ImplicitSearchResult, Name}
import org.jetbrains.plugins.scala.lang.completion.ScalaGlobalMembersCompletionContributor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible.ImplicitMapResult
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}

/**
  * Created by Svyatoslav Ilinskiy on 25.07.16.
  */
class SearchForImplicitClassAction(val element: ScReferenceExpression, qualifier: ScExpression) extends IntentionAction with SearchImplicitPopup {
  override def getFamilyName: String = Name

  override def searchingTitleText: String = ScalaBundle.message("searching.for.implicit.classes")

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    val implicits: Option[Seq[ImplicitSearchResult]] = searchWithProgress { () =>
      val funName = Option(element.nameId.getText)
      val originalType = qualifier.getType().getOrNothing
      findImplicitConversion(element, originalType, funName)
    }
    implicits.foreach { funs =>
      showPopup(funs.toArray, editor)
    }
  }

  def findImplicitConversion(ref: ScExpression, originalType: ScType, name: Option[String] = None): Seq[ImplicitSearchResult] = {
    implicit val typeSystem = ref.typeSystem
    val conversions = ScalaGlobalMembersCompletionContributor.findImplicitConversions(ref, originalType)
    conversions.toSeq.flatMap {
      case ImplicitMapResult(true, resolveResult, _, retTp, _, _, _) =>
        val c = new CompletionProcessor(StdKinds.methodRef, ref)
        c.processType(retTp, ref)
        c.candidates.collect {
          case ScalaResolveResult(fun: ScFunction, _) if name.forall(_ == fun.name) =>
            ImplicitSearchResult(fun, resolveResult.getActualElement)
        }
      case _ => Seq.empty
    }
  }

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    file.isInstanceOf[ScalaFile] && element.isValid
  }

  override def getText: String = getFamilyName

  override def startInWriteAction(): Boolean = false
}

object SearchForImplicitClassAction {
  val Name = ScalaBundle.message("search.for.implicit.class")

  case class ImplicitSearchResult(fun: ScFunction, elementToImport: PsiNamedElement)
}

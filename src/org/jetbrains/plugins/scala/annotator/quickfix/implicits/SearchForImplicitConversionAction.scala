package org.jetbrains.plugins.scala.annotator.quickfix.implicits

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.StubIndex
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.quickfix.implicits.SearchForImplicitClassAction.ImplicitSearchResult
import org.jetbrains.plugins.scala.annotator.quickfix.implicits.SearchForImplicitConversionAction.Name
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.types.result.Success

import scala.collection.JavaConverters._

/**
  * Created by Svyatoslav Ilinskiy on 28.07.16.
  */
class SearchForImplicitConversionAction(val element: ScExpression, originalType: ScType, expected: ScType)
  extends IntentionAction with SearchImplicitPopup {

  override def getFamilyName: String = Name

  override def getText: String = getFamilyName

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    implicit val typeSystem = file.typeSystem
    val results = searchWithProgress { () =>
      val allImplicits = StubIndex.getElements(ScalaIndexKeys.IMPLICITS_KEY, ScalaKeyword.IMPLICIT, project, element.getResolveScope, classOf[ScMember])
      allImplicits.asScala.toSeq.flatMap {
        case fun: ScFunction => fun.getType() match {
          case Success(FunctionType(retTp, Seq(param)), _) if originalType.conforms(param) && retTp.conforms(expected) =>
            Some(ImplicitSearchResult(fun, fun))
          case _ => None
        }
        case _ => None
      }
    }
    results.foreach { implicits =>
      showPopup(implicits.toArray, editor)
    }
  }

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = file.isInstanceOf[ScalaFile]

  override def startInWriteAction(): Boolean = false

  override def searchingTitleText: String = ScalaBundle.message("searching.for.implicit.conversions")
}

object SearchForImplicitConversionAction {
  val Name = ScalaBundle.message("search.for.implicit.conversions")
}

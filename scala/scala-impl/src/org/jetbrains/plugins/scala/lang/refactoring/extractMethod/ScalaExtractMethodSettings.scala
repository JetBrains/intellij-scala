package org.jetbrains.plugins.scala.lang.refactoring.extractMethod

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

class ScalaExtractMethodSettings(
  val methodName: String,
  val parameters: Array[ExtractMethodParameter],
  val outputs: Array[ExtractMethodOutput],
  val visibility: String,
  val nextSibling: PsiElement,
  val elements: Array[PsiElement],
  val returnType: Option[ScType],
  val addReturnType: ScalaApplicationSettings.ReturnTypeLevel,
  val lastReturn: Boolean,
  val lastExprType: Option[ScType],
  val innerClassSettings: InnerClassSettings
) {

  def projectContext: ProjectContext = nextSibling.getProject

  lazy val (calcReturnTypeIsUnit, calcReturnTypeText) = ScalaExtractMethodUtils.calcReturnTypeExt(this)

  val typeParameters: Seq[ScTypeParam] = {
    val nextRange = nextSibling.getTextRange

    val elem: PsiElement = elements.head
    elem.parentsInFile
      .takeWhile { parent =>
        parent != null && ! {
          val range = parent.getTextRange
          range != null &&
            range.contains(nextRange) &&
            !range.equalsToRange(nextRange.getStartOffset, nextRange.getEndOffset)
        }
      }
      .collect { case tpo: ScTypeParametersOwner => tpo}
      .flatMap(_.typeParameters)
      .toSeq
      .reverse
  }
}
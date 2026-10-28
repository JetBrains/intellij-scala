package org.jetbrains.plugins.scala.codeInsight.hints.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering.CodeVisionRelativeOrderingBefore
import com.intellij.codeInsight.hints.codeVision.CodeVisionProviderBase.CodeVisionInfo
import com.intellij.codeInsight.hints.codeVision.ReferencesCodeVisionProvider
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

import java.util

/**
 * Shows the number of usages above Scala declarations, like `JavaReferencesCodeVisionProvider` does for Java.
 */
final class ScalaReferencesCodeVisionProvider extends ReferencesCodeVisionProvider {

  import ScalaReferencesCodeVisionProvider._

  override def acceptsFile(file: PsiFile): Boolean = ScalaCodeVision.acceptsFile(file)

  override def acceptsElement(element: PsiElement): Boolean = element match {
    case td: ScTypeDefinition => !td.isLocal
    case fn: ScFunction       => !fn.isLocal && !fn.isConstructor
    case v: ScValueOrVariable => !v.isLocal
    case ta: ScTypeAlias      => !ta.isLocal
    case _                    => false
  }

  override def getVisionInfo(element: PsiElement, file: PsiFile): CodeVisionInfo = {
    val count = usagesCount(element, file)
    if (count <= 0) null
    else new CodeVisionInfo(ScalaBundle.message("code.vision.usages.hint", count), count, true)
  }

  override def getHint(element: PsiElement, file: PsiFile): String =
    Option(getVisionInfo(element, file)).map(_.getText).orNull

  override def getId: String = ScalaCodeVision.ReferencesId

  override def getRelativeOrderings: util.List[CodeVisionRelativeOrdering] =
    util.List.of(new CodeVisionRelativeOrderingBefore(ScalaCodeVision.InheritorsId))
}

private object ScalaReferencesCodeVisionProvider {
  private def usagesCount(element: PsiElement, file: PsiFile): Int = {
    val namedElements: Seq[ScNamedElement] = element match {
      case v: ScValueOrVariable => v.declaredElements
      case named: ScNamedElement => Seq(named)
      case _ => Seq.empty
    }
    val manager = ScalaUsagesCountManager.getInstance(file.getProject)
    namedElements.foldLeft(0) {
      case (ScalaUsagesCountManager.TooManyUsages, _) => ScalaUsagesCountManager.TooManyUsages
      case (sum, named) =>
        manager.countMemberUsages(file, named) match {
          case ScalaUsagesCountManager.TooManyUsages => ScalaUsagesCountManager.TooManyUsages
          case count                                 => sum + count
        }
    }
  }
}

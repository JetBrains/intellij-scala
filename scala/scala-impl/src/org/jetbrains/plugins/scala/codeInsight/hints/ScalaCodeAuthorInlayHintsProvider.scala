package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.codeInsight.daemon.impl.JavaCodeVisionUsageCollector._
import com.intellij.codeInsight.hints.VcsCodeAuthorInlayHintsProvider
import com.intellij.psi.PsiElement
import kotlin.jvm.functions.{Function0 => KtFunction0}
import org.jetbrains.plugins.scala.codeInsight.hints.ScalaCodeAuthorInlayHintsProvider._
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

//noinspection UnstableApiUsage
class ScalaCodeAuthorInlayHintsProvider extends VcsCodeAuthorInlayHintsProvider {
  override def isAccepted(element: PsiElement): Boolean = isAcceptedTemplateDefinition(element) || isAcceptedFunction(element)

  override def getClickHandler(element: PsiElement): KtFunction0[kotlin.Unit] = {
    val project = element.getProject
    val location = if (isAcceptedTemplateDefinition(element)) CLASS_LOCATION else METHOD_LOCATION

    toKotlinFunction(logCodeAuthorClicked(project, location))
  }
}

object ScalaCodeAuthorInlayHintsProvider {
  private def isAcceptedTemplateDefinition(element: PsiElement): Boolean =
    element.is[ScClass, ScObject, ScTrait, ScEnum, ScGivenDefinition]

  private def isAcceptedFunction(element: PsiElement): Boolean = element.is[ScFunction]

  private def toKotlinFunction(body: => Unit): KtFunction0[kotlin.Unit] = () => {
    body
    kotlin.Unit.INSTANCE
  }
}

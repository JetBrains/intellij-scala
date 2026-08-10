package org.jetbrains.plugins.scala.bazel

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.bazel.java.ui.gutters.BazelJavaRunLineMarkerContributor
import org.jetbrains.bazel.runnerAction.BazelRunnerActionDescriptor
import org.jetbrains.bazel.ui.gutters.BazelRunLineMarkerContributor.GutterAction
import org.jetbrains.plugins.scala.incremental.Highlighting.ElementHighlightingExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import java.util
import scala.jdk.CollectionConverters.SeqHasAsJava

/**
 *  - Inherits logic for running scala from BazelJavaRunLineMarkerContributor
 *  - Adds logic for running entire test classes and individual tests via Bazel (see [[BazelScalaTestRunLineMarkerLogic]]
 */
// TODO(BAZEL-3360): use BazelRunLineMarkerContributor
//   BazelJavaRunLineMarkerContributor's comment says: "External plugins (e.g., Scala) should implement BazelRunLineMarkerContributor instead"
class BazelScalaRunLineMarkerContributor extends BazelJavaRunLineMarkerContributor {

  @Nullable
  override def getGutterAction(psiElement: PsiElement): GutterAction = {
    val file = psiElement.getContainingFile
    val project = if (file != null) file.getProject else psiElement.getProject // Avoid tree walk-up

    if (!ScalaProjectSettings.in(project).isDisableInspections) {
      if (!psiElement.isVisible(project, file)) return null
    }

    val action = super.getGutterAction(psiElement)
    if (action != null) return action

    if (!BazelScalaTestRunLineMarkerLogic.shouldAddMarker(psiElement)) null
    else {
      val testFilter = BazelScalaTestRunLineMarkerLogic.getSingleTestFilter(psiElement)
      if (testFilter == null) return null

      val arguments = BazelScalaTestRunLineMarkerLogic.getExtraProgramArguments(psiElement).asJava
      val descriptor = new BazelRunnerActionDescriptor(testFilter, arguments, util.Map.of())
      new GutterAction(descriptor)
    }
  }
}

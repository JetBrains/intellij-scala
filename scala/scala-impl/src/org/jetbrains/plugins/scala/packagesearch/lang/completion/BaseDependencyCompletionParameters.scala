package org.jetbrains.plugins.scala.packagesearch.lang.completion

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionResultSet}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil

abstract class BaseDependencyCompletionParameters[Element <: PsiElement](
  val completionParams: CompletionParameters,
  val resultSet: CompletionResultSet,
  val place: Element,
) {
  /**
   * The Scala version(s) used in the project. Invariant for a single completion session, so it is computed
   * once here instead of per suggested lookup item.
   */
  lazy val scalaVersions: Seq[String] = DependencyUtil.getAllScalaVersionsOrDefault(place)
}

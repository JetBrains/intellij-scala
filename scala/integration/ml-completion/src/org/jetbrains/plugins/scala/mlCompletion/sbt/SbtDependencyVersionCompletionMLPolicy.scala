package org.jetbrains.plugins.scala.mlCompletion.sbt

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionType}
import com.intellij.completion.ml.CompletionMLPolicy
import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiElement

//noinspection ApiStatus,UnstableApiUsage
// TODO: Reimplement when https://youtrack.jetbrains.com/issue/IDEA-272935 is fixed
abstract class SbtDependencyVersionCompletionMLPolicy extends CompletionMLPolicy {
  protected def VERSION_PATTERN: ElementPattern[? <: PsiElement]

  override def isReRankingDisabled(params: CompletionParameters): Boolean =
    params.getCompletionType == CompletionType.BASIC && VERSION_PATTERN.accepts(params.getPosition)
}

package org.jetbrains.plugins.scalaDirective.lang.completion.weigher

import com.intellij.codeInsight.completion.impl.NegatingComparable
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementWeigher}
import org.apache.maven.artifact.versioning.ComparableVersion

object ScalaDirectiveDependencyVersionWeigher extends LookupElementWeigher("scalaDirectiveDependencyVersionWeigher") {
  override def weigh(element: LookupElement): Comparable[_] = {
    val version = element.getObject match {
      case ScalaDirectiveVersion(version) => version
      case _ => new ComparableVersion("0.0.0")
    }
    new NegatingComparable(version)
  }

  final case class ScalaDirectiveVersion(version: ComparableVersion)
}

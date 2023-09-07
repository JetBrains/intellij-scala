package org.jetbrains.plugins.scalaDirective.lang.completion.weigher

import com.intellij.codeInsight.completion.impl.NegatingComparable
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementWeigher}
import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.plugins.scalaDirective.dependencies.ScalaDirectiveDependencyDescriptor

object ScalaDirectiveDependencyVersionWeigher extends LookupElementWeigher("scalaDirectiveDependencyVersionWeigher") {
  override def weigh(element: LookupElement): Comparable[_] = {
    val maybeVersion = element.getLookupString match {
      case ScalaDirectiveDependencyDescriptor(descriptor) => descriptor.version
      case _ => None
    }
    val version = maybeVersion.getOrElse("0.0.0")
    new NegatingComparable(new ComparableVersion(version))
  }
}

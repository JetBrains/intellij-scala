package org.jetbrains.plugins.scalaDirective.lang.completion.lookups

import com.intellij.codeInsight.lookup.LookupElement
import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.plugins.scala.packagesearch.codeInspection.DependencyVersionInspection.DependencyDescriptor
import org.jetbrains.plugins.scala.packagesearch.lang.completion.DependencyVersionWeigher.DependencyVersion
import org.jetbrains.plugins.scalaDirective.dependencies.ScalaDirectiveDependencyDescriptor
import org.jetbrains.plugins.scalaDirective.util.ScalaDirectiveValueKind

object ScalaDirectiveDependencyVersionLookupItem {
  def apply(version: ComparableVersion, descriptor: DependencyDescriptor, valueKind: ScalaDirectiveValueKind): LookupElement = {
    val updatedDescriptor = descriptor.copy(version = Some(version.toString))
    val text = ScalaDirectiveDependencyDescriptor.render(updatedDescriptor)

    ScalaDirectiveDependencyLookupItem(text, DependencyVersion(version), valueKind)
  }

  def apply(version: ComparableVersion, valueKind: ScalaDirectiveValueKind): LookupElement =
    ScalaDirectiveDependencyLookupItem(version.toString, DependencyVersion(version), valueKind)
}

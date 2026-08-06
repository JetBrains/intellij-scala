package org.jetbrains.sbt.project.directoryCompletion

import org.jetbrains.jps.model.module.JpsModuleSourceRootType

final case class ExpectedDirectoryCompletionVariant(
  projectRelativePath: String,
  rootType: JpsModuleSourceRootType[?]
)

object ExpectedDirectoryCompletionVariant {
  implicit val expectedDirectoryCompletionVariantOrdering: Ordering[ExpectedDirectoryCompletionVariant] =
    (x: ExpectedDirectoryCompletionVariant, y: ExpectedDirectoryCompletionVariant) =>
      x.projectRelativePath compare y.projectRelativePath
}
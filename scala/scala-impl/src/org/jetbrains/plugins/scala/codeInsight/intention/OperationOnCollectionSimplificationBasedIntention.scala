package org.jetbrains.plugins.scala.codeInsight.intention

import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.collections.{OperationOnCollectionQuickFix, SimplificationType}

abstract class OperationOnCollectionSimplificationBasedIntention(
  @Nls familyName: String,
  simplificationType: SimplificationType
) extends SimplificationBasedIntention(familyName, simplificationType, quickFix = OperationOnCollectionQuickFix(_))

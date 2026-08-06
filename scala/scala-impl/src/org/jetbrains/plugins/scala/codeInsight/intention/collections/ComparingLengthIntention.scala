package org.jetbrains.plugins.scala.codeInsight.intention.collections

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intention.OperationOnCollectionSimplificationBasedIntention
import org.jetbrains.plugins.scala.codeInspection.collections.ComparingLengthInspection

final class ComparingLengthIntention extends OperationOnCollectionSimplificationBasedIntention(
  ScalaBundle.message("family.name.comparing.length"),
  ComparingLengthInspection
)

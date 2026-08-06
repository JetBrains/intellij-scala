package org.jetbrains.plugins.scala.codeInsight.intention.collections

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intention.OperationOnCollectionSimplificationBasedIntention
import org.jetbrains.plugins.scala.codeInspection.collections.FilterSetContainsInspection

final class FilterSetContainsIntention extends OperationOnCollectionSimplificationBasedIntention(
  ScalaBundle.message("family.name.filter.set.contains"),
  FilterSetContainsInspection
)

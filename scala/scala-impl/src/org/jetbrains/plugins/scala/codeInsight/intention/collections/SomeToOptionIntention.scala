package org.jetbrains.plugins.scala.codeInsight.intention.collections

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intention.OperationOnCollectionSimplificationBasedIntention
import org.jetbrains.plugins.scala.codeInspection.collections.SomeToOptionInspection

final class SomeToOptionIntention extends OperationOnCollectionSimplificationBasedIntention(
  ScalaBundle.message("family.name.some.to.option"),
  SomeToOptionInspection
)

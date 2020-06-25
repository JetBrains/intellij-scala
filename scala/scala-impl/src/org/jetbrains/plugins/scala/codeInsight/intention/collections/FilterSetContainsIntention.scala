package org.jetbrains.plugins.scala.codeInsight.intention.collections

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intention.InspectionBasedIntention
import org.jetbrains.plugins.scala.codeInspection.collections.FilterSetContainsInspection

class FilterSetContainsIntention
  extends InspectionBasedIntention(ScalaBundle.message("family.name.filter.set.contains"), FilterSetContainsInspection.hint, new FilterSetContainsInspection)
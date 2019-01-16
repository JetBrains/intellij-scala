package org.jetbrains.plugins.scala.codeInsight.intention.collections

import org.jetbrains.plugins.scala.codeInsight.intention.InspectionBasedIntention
import org.jetbrains.plugins.scala.codeInspection.collections.FilterSetContainsInspection

class FilterSetContainsIntention
  extends InspectionBasedIntention("Filter Set Contains ", FilterSetContainsInspection.hint, new FilterSetContainsInspection)
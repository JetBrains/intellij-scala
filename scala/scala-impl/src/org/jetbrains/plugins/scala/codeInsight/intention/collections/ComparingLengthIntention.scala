package org.jetbrains.plugins.scala.codeInsight.intention.collections

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intention.InspectionBasedIntention
import org.jetbrains.plugins.scala.codeInspection.collections.ComparingLengthInspection

class ComparingLengthIntention
  extends InspectionBasedIntention(ScalaBundle.message("family.name.comparing.length"), new ComparingLengthInspection)

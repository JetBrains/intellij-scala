package org.jetbrains.plugins.scala.codeInsight.intention.collections

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intention.InspectionBasedIntention
import org.jetbrains.plugins.scala.codeInspection.collections.ComparingLengthInspection

/**
  * Nikolay.Tropin
  * 18-Jan-18
  */
class ComparingLengthIntention
  extends InspectionBasedIntention(ScalaBundle.message("family.name.comparing.length"), ComparingLengthInspection.hint, new ComparingLengthInspection)

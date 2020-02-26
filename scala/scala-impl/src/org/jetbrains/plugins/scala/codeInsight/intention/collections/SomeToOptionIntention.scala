package org.jetbrains.plugins.scala.codeInsight.intention.collections

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intention.InspectionBasedIntention
import org.jetbrains.plugins.scala.codeInspection.collections.SomeToOptionInspection

class SomeToOptionIntention
  extends InspectionBasedIntention(ScalaBundle.message("family.name.some.to.option"), SomeToOptionInspection.hint, new SomeToOptionInspection)
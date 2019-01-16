package org.jetbrains.plugins.scala.codeInsight.intention.collections

import org.jetbrains.plugins.scala.codeInsight.intention.InspectionBasedIntention
import org.jetbrains.plugins.scala.codeInspection.collections.SomeToOptionInspection

class SomeToOptionIntention
  extends InspectionBasedIntention("Some to Option", SomeToOptionInspection.hint, new SomeToOptionInspection)
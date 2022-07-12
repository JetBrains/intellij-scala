package org.jetbrains.plugins.scala.codeInspection.modifier

import org.jetbrains.plugins.scala.codeInspection.{ScalaAnnotatorQuickFixTestBase, ScalaInspectionBundle}

class AccessModifierCanBeWeakerInspectionQuickFixTest extends ScalaAnnotatorQuickFixTestBase {

  override protected val description = ScalaInspectionBundle.message("access.modifier.can.be.weaker")

}

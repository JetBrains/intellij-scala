package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.quickfix

import org.jetbrains.plugins.scala.codeInspection.{ScalaAnnotatorQuickFixTestBase, ScalaInspectionBundle}

class MakePrivateQuickFixTest extends ScalaAnnotatorQuickFixTestBase {

  override protected val description = "Access can be private"

}

package org.jetbrains.plugins.scala.codeInspection.syntacticClarification

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.transformation.calls.ExpandApplyCall

/**
  * @author Pavel Fatin
  */
// TODO add checkboxes to exclude FunctionN descendants and case classes
class ConcealedApplyCall extends TransformerBasedInspection(
  ScalaInspectionBundle.message("concealed.apply.call"),
  ScalaInspectionBundle.message("make.apply.call.explicit"),
  new ExpandApplyCall()
)

package org.jetbrains.plugins.scala.codeInspection.syntacticClarification

import org.jetbrains.plugins.scala.lang.transformation.calls.ExpandApplyCall

/**
  * @author Pavel Fatin
  */
// TODO add checkboxes to exclude FunctionN descendants and case classes
class ConcealedApplyCall extends TransformerBasedInspection(
  "Concealed \"apply\" call",
  "Make \"apply\" call explicit",
  new ExpandApplyCall())

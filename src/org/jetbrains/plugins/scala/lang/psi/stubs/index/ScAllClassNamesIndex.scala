package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.psi.PsiClass
import com.intellij.psi.stubs.StringStubIndexExtension

/**
 * User: Alefas
 * Date: 10.02.12
 */

class ScAllClassNamesIndex extends StringStubIndexExtension[PsiClass] {
  def getKey = ScAllClassNamesIndex.KEY
}

object ScAllClassNamesIndex {
  val KEY = ScalaIndexKeys.ALL_CLASS_NAMES
}

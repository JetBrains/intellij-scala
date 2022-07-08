package org.jetbrains.plugins.scala.lang.psi
package stubs
package index

import com.intellij.psi.PsiClass
import com.intellij.psi.stubs.StubIndexKey

class ScAllClassNamesIndex extends ScStringStubIndexExtension[PsiClass] {
  override def getKey: StubIndexKey[String, PsiClass] =
    ScalaIndexKeys.ALL_CLASS_NAMES
}

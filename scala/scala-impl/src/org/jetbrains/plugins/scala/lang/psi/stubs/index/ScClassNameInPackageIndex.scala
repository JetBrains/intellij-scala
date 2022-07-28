package org.jetbrains.plugins.scala.lang.psi
package stubs
package index

import com.intellij.psi.PsiClass
import com.intellij.psi.stubs.StubIndexKey

class ScClassNameInPackageIndex extends ScStringStubIndexExtension[PsiClass] {
  override def getKey: StubIndexKey[String, PsiClass] =
    ScalaIndexKeys.CLASS_NAME_IN_PACKAGE_KEY
}

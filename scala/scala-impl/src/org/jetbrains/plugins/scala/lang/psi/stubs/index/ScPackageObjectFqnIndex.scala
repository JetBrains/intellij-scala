package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.psi.PsiClass
import com.intellij.psi.stubs.StubIndexKey

final class ScPackageObjectFqnIndex extends ScFqnHashStubIndexExtension[PsiClass] {

  override def getKey: StubIndexKey[CharSequence, PsiClass] =
    ScalaIndexKeys.PACKAGE_OBJECT_FQN_KEY
}

object ScPackageObjectFqnIndex {
  def instance: ScPackageObjectFqnIndex = new ScPackageObjectFqnIndex
}

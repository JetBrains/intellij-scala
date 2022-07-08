package org.jetbrains.plugins.scala.lang.psi
package stubs
package index

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging

class ScFullPackagingNameIndex extends ScIntStubIndexExtension[ScPackaging] {

  override def getKey: StubIndexKey[Integer, ScPackaging] =
    ScalaIndexKeys.PACKAGE_FQN_KEY
}

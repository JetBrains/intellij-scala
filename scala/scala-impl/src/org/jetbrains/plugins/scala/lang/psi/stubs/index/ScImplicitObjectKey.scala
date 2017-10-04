package org.jetbrains.plugins.scala.lang.psi
package stubs
package index

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

/**
  * @author Alexander Podkhalyuzin
  */
class ScImplicitObjectKey extends ScStringStubIndexExtension[ScObject] {

  override def getKey: StubIndexKey[String, ScObject] =
    ScalaIndexKeys.IMPLICIT_OBJECT_KEY
}

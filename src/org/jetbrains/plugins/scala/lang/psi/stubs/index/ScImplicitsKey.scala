package org.jetbrains.plugins.scala.lang.psi
package stubs
package index

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

/**
 * @author Alexander Podkhalyuzin
 */

class ScImplicitsKey extends ScStringStubIndexExtension[ScMember] {

  override def getKey: StubIndexKey[String, ScMember] =
    ScalaIndexKeys.IMPLICITS_KEY
}

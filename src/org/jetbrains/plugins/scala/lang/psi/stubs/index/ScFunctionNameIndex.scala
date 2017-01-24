package org.jetbrains.plugins.scala.lang.psi
package stubs
package index

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
  * User: Alexander Podkhalyuzin
  * Date: 14.10.2008
  */
class ScFunctionNameIndex extends ScStringStubIndexExtension[ScFunction] {

  override def getKey: StubIndexKey[String, ScFunction] =
    ScalaIndexKeys.METHOD_NAME_KEY
}

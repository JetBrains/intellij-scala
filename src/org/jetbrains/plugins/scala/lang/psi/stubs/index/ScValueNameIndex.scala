package org.jetbrains.plugins.scala.lang.psi
package stubs
package index

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.10.2008
  */
class ScValueNameIndex extends ScStringStubIndexExtension[ScValue] {

  override def getKey: StubIndexKey[String, ScValue] =
    ScalaIndexKeys.VALUE_NAME_KEY
}

class ScClassParameterNameIndex extends ScStringStubIndexExtension[ScClassParameter] {

  override def getKey: StubIndexKey[String, ScClassParameter] =
    ScalaIndexKeys.CLASS_PARAMETER_NAME_KEY
}

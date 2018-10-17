package org.jetbrains.plugins.scala.lang.psi
package stubs
package index

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter

final class ScClassParameterNameIndex extends ScStringStubIndexExtension[ScClassParameter] {

  override def getKey: StubIndexKey[String, ScClassParameter] = ScalaIndexKeys.CLASS_PARAMETER_NAME_KEY
}

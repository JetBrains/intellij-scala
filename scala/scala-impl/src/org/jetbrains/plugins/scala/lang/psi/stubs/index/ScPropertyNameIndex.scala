package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable

final class ScPropertyNameIndex extends ScStringStubIndexExtension[ScValueOrVariable] {

  override def getKey: StubIndexKey[String, ScValueOrVariable] = ScalaIndexKeys.PROPERTY_NAME_KEY
}

package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable

class ScPropertyClassNameIndex extends ScStringStubIndexExtension[ScValueOrVariable] {

  override def getKey: StubIndexKey[String, ScValueOrVariable] = ScalaIndexKeys.PROPERTY_CLASS_NAME_KEY
}

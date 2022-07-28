package org.jetbrains.plugins.scala.lang.psi
package stubs
package index

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

class ScTypeAliasNameIndex extends ScStringStubIndexExtension[ScTypeAlias] {

  override def getKey: StubIndexKey[String, ScTypeAlias] =
    ScalaIndexKeys.TYPE_ALIAS_NAME_KEY
}

class ScStableTypeAliasNameIndex extends ScStringStubIndexExtension[ScTypeAlias] {
  override def getKey: StubIndexKey[String, ScTypeAlias] =
    ScalaIndexKeys.STABLE_ALIAS_NAME_KEY
}

class ScStableTypeAliasFqnIndex extends ScIntStubIndexExtension[ScTypeAlias] {
  override def getKey: StubIndexKey[Integer, ScTypeAlias] =
    ScalaIndexKeys.STABLE_ALIAS_FQN_KEY
}

class ScAliasedClassNameKey extends ScStringStubIndexExtension[ScTypeAlias] {
  override def getKey: StubIndexKey[String, ScTypeAlias] =
    ScalaIndexKeys.ALIASED_CLASS_NAME_KEY
}

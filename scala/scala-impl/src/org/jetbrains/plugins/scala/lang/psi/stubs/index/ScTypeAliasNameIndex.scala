package org.jetbrains.plugins.scala.lang.psi.stubs.index

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

class ScStableTypeAliasFqnIndex extends ScFqnHashStubIndexExtension[ScTypeAlias] {
  override def getKey: StubIndexKey[CharSequence, ScTypeAlias] =
    ScalaIndexKeys.STABLE_ALIAS_FQN_KEY
}
object ScStableTypeAliasFqnIndex {
  def instance: ScStableTypeAliasFqnIndex = new ScStableTypeAliasFqnIndex
}

class ScAliasedClassNameKey extends ScStringStubIndexExtension[ScTypeAlias] {
  override def getKey: StubIndexKey[String, ScTypeAlias] =
    ScalaIndexKeys.ALIASED_CLASS_NAME_KEY
}

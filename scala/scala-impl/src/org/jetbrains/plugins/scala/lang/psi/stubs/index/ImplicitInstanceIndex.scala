package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.psi.stubs.StubIndexKey
import com.intellij.psi.stubs.StubIndexKey.createIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

class ImplicitInstanceIndex extends ScStringStubIndexExtension[ScMember]  {
  def getKey: StubIndexKey[String, ScMember] = ImplicitInstanceIndex.indexKey
}

object ImplicitInstanceIndex extends ImplicitIndex {
  val indexKey: StubIndexKey[String, ScMember] = createIndexKey("sc.implicit.instance")
}
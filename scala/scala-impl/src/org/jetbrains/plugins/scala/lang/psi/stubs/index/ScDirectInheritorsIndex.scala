package org.jetbrains.plugins.scala.lang.psi
package stubs
package index

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock

class ScDirectInheritorsIndex extends ScStringStubIndexExtension[ScExtendsBlock] {

  override def getKey: StubIndexKey[String, ScExtendsBlock] =
    ScalaIndexKeys.SUPER_CLASS_NAME_KEY
}

class ScSelfTypeInheritorsIndex extends ScStringStubIndexExtension[ScSelfTypeElement] {

  override def getKey: StubIndexKey[String, ScSelfTypeElement] =
    ScalaIndexKeys.SELF_TYPE_CLASS_NAME_KEY
}

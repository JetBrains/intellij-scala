package org.jetbrains.plugins.scala.lang.psi
package stubs
package index

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation

class ScAnnotatedMemberIndex extends ScStringStubIndexExtension[ScAnnotation] {
  override def getKey: StubIndexKey[String, ScAnnotation] =
    ScalaIndexKeys.ANNOTATED_MEMBER_KEY
}

package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import com.intellij.psi.stubs.{StringStubIndexExtension, StubIndexKey}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation

/**
 * @author ilyas
 */

class ScAnnotatedMemberIndex extends StringStubIndexExtension[ScAnnotation] {
  def getKey: StubIndexKey[String, ScAnnotation] = ScAnnotatedMemberIndex.KEY
}

object ScAnnotatedMemberIndex {
  val KEY = ScalaIndexKeys.ANNOTATED_MEMBER_KEY
}
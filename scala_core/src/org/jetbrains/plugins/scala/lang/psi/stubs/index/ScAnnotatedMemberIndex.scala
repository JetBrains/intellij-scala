package org.jetbrains.plugins.scala.lang.psi.stubs.index

import api.toplevel.typedef.ScMember
import com.intellij.psi.stubs.{StringStubIndexExtension, StubIndexKey}
/**
 * @author ilyas
 */

class ScAnnotatedMemberIndex extends StringStubIndexExtension[ScMember] {
  def getKey: StubIndexKey[String, ScMember] = ScAnnotatedMemberIndex.KEY
}

object ScAnnotatedMemberIndex {
  val KEY = ScalaIndexKeys.ANNOTATED_MEMBER_KEY
}
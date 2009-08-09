package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index
import com.intellij.psi.PsiClass
import api.toplevel.typedef.ScTypeDefinition
import com.intellij.psi.stubs.{StringStubIndexExtension, StubIndexKey}

/**
 * @author ilyas
 */

class ScShortClassNameIndex extends StringStubIndexExtension[PsiClass] {
  def getKey = ScShortClassNameIndex.KEY
}

object ScShortClassNameIndex {
  val KEY = ScalaIndexKeys.SHORT_NAME_KEY;
}
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
  val KEY = ScalaIndexKeys.SHORT_NAME_KEY
}

class ScNotVisibleInJavaShortClassNameIndex extends StringStubIndexExtension[PsiClass] {
  def getKey = ScNotVisibleInJavaShortClassNameIndex.KEY
}

object ScNotVisibleInJavaShortClassNameIndex {
  val KEY = ScalaIndexKeys.NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY
}

class ScShortNamePackageObjectIndex extends StringStubIndexExtension[PsiClass] {
  def getKey = ScShortNamePackageObjectIndex.KEY
}

object ScShortNamePackageObjectIndex {
  val KEY = ScalaIndexKeys.PACKAGE_OBJECT_SHORT_NAME_KEY
}
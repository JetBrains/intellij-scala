package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi._

class ScFileStubImpl(file: api.ScalaFile,
                     override val getType: tree.IStubFileElementType[ScFileStub])
  extends stubs.PsiFileStubImpl(file) with ScFileStub {

  override def sourceName: String = file.sourceName

  override def isScript: Boolean = file.isScriptFileImpl

  override final def getClasses: Array[PsiClass] = getChildrenByType(
    TokenSets.TYPE_DEFINITIONS,
    PsiClass.ARRAY_FACTORY
  )
}

package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.stubs.{PsiFileStub, PsiFileStubImpl}
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.psi.{PsiClass, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
  * @author ilyas
  */
abstract class AbstractFileStub(file: ScalaFile)
  extends PsiFileStubImpl[ScalaFile](file) with ScFileStub {
  override def getClasses: Array[PsiClass] =
    getChildrenByType(tokenSets.typeDefinitions, PsiClass.ARRAY_FACTORY)

  override def getType: IStubFileElementType[Nothing] =
    fileElementType.asInstanceOf[IStubFileElementType[Nothing]]

  protected def tokenSets: TokenSets =
    ScalaTokenSets

  protected def fileElementType: IStubFileElementType[_ <: PsiFileStub[_ <: PsiFile]] =
    tokenSets.elementTypes.file
}

class ScFileStubImpl(file: ScalaFile) extends AbstractFileStub(file) {
  override def packageName: String = file.packageName

  override def sourceName: String = file.sourceName

  override def isCompiled: Boolean = file.isCompiled

  override def isScript: Boolean = file.isScriptFile(withCaching = false)
}

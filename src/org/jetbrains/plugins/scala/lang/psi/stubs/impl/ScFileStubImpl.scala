package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiClass
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.wrappers.PsiFileStubWrapperImpl

/**
 * @author ilyas
 */

class ScFileStubImpl(file: ScalaFile,
                     val packName: StringRef,
                     val fileName: StringRef,
                     val isCompiled: Boolean = false,
                     val isScript: Boolean = false
                    ) extends PsiFileStubWrapperImpl[ScalaFile](file) with ScFileStub {
  protected lazy val tokenSets: TokenSets = ScalaTokenSets

  override def getType = tokenSets.elementTypes.file.asInstanceOf[IStubFileElementType[Nothing]]

  def getClasses = getChildrenByType(tokenSets.templateDefinitionSet, PsiClass.ARRAY_FACTORY)

  def getFileName = StringRef.toString(fileName)

  def packageName = StringRef.toString(packName)
}
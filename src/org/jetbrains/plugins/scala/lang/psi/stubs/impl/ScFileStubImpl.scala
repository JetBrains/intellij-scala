package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl
import api.ScalaFile
import com.intellij.psi.PsiClass
import com.intellij.psi.stubs.{StubElement, PsiFileStubImpl}
import com.intellij.psi.tree.{TokenSet, IStubFileElementType}
import elements.{ScTypeAliasElementType, ScVariableElementType, ScValueElementType, ScPackageContainerElementType}
import parser.ScalaElementTypes
import elements.wrappers.PsiFileStubWrapperImpl
import com.intellij.util.io.StringRef
/**
 * @author ilyas
 */

class ScFileStubImpl(file: ScalaFile) extends PsiFileStubWrapperImpl[ScalaFile](file) with ScFileStub {

  override def getType = ScalaElementTypes.FILE.asInstanceOf[IStubFileElementType[Nothing]]

  implicit  def refToStr(ref: StringRef) = StringRef.toString(ref)

  var packName: StringRef = _
  var sourceFileName: StringRef = _
  var compiled: Boolean = false
  var script: Boolean = false

  def this(file: ScalaFile, pName : StringRef, name: StringRef, compiled: Boolean, script: Boolean) = {
    this(file)
    this.sourceFileName = name
    packName = pName
    this.compiled = compiled
    this.script = script
  }

  def getClasses = {
    import ScalaElementTypes._
    getChildrenByType(TokenSet.create(CLASS_DEF, OBJECT_DEF, TRAIT_DEF), PsiClass.ARRAY_FACTORY)
  }

  def getFileName = sourceFileName

  def packageName = packName

  def isCompiled: Boolean = compiled

  def isScript: Boolean = script
}
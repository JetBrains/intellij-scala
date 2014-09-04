package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl
import com.intellij.psi.PsiClass
import com.intellij.psi.tree.{IStubFileElementType, TokenSet}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.wrappers.PsiFileStubWrapperImpl
/**
 * @author ilyas
 */

class ScFileStubImpl(file: ScalaFile) extends PsiFileStubWrapperImpl[ScalaFile](file) with ScFileStub {

  override def getType = ScalaElementTypes.FILE.asInstanceOf[IStubFileElementType[Nothing]]

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
    import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes._
    getChildrenByType(TokenSet.create(CLASS_DEF, OBJECT_DEF, TRAIT_DEF), PsiClass.ARRAY_FACTORY)
  }

  def getFileName = StringRef.toString(sourceFileName)

  def packageName = StringRef.toString(packName)

  def isCompiled: Boolean = compiled

  def isScript: Boolean = script
}
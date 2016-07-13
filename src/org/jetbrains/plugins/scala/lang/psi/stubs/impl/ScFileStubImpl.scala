package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiClass
import com.intellij.psi.stubs.{StubInputStream, StubOutputStream}
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.wrappers.PsiFileStubWrapperImpl

/**
  * @author ilyas
  */
class ScFileStubImpl(file: ScalaFile,
                     val isScript: Boolean,
                     val isCompiled: Boolean,
                     private val packageNameRef: StringRef,
                     private val sourceNameRef: StringRef)
  extends PsiFileStubWrapperImpl[ScalaFile](file) with ScFileStub {

  def this(file: ScalaFile) = {
    this(file,
      file.isScriptFile(withCaching = false),
      file.isCompiled,
      StringRef.fromString(file.packageName),
      StringRef.fromString(file.sourceName))
  }

  override def getType: IStubFileElementType[Nothing] = ScalaElementTypes.FILE.asInstanceOf[IStubFileElementType[Nothing]]

  override def getClasses: Array[PsiClass] =
    getChildrenByType(TokenSets.TYPE_DEFINITIONS_SET, PsiClass.ARRAY_FACTORY)

  override def packageName = StringRef.toString(packageNameRef)

  override def sourceName = StringRef.toString(sourceNameRef)

  override def serializeTo(dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(isScript)
    dataStream.writeBoolean(isCompiled)
    dataStream.writeName(packageName)
    dataStream.writeName(sourceName)
  }
}

object ScFileStubImpl {
  def deserializeFrom(dataStream: StubInputStream) = new ScFileStubImpl(null,
    dataStream.readBoolean,
    dataStream.readBoolean,
    dataStream.readName,
    dataStream.readName)
}
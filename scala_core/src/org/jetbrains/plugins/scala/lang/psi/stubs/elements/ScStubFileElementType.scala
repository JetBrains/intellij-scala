package org.jetbrains.plugins.scala.lang.psi.stubs.elements
import com.intellij.psi.stubs.IndexSink
import com.intellij.lang.Language
import com.intellij.psi.tree.IStubFileElementType

/**
 * @author ilyas
 */

class ScStubFileElementType(lang: Language) extends IStubFileElementType[ScFileStub](lang) {


  override def getBuilder = new ScalaFileStubBuilder()

  override def getExternalId = "scala.FILE"

  def indexStub(stub: ScFileStub, sink: IndexSink){
    //do nothing
  }

}
package org.jetbrains.plugins.scala
package decompiler

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.compiled.ClsStubBuilderFactory

import com.intellij.psi.stubs.{PsiFileStubImpl, PsiFileStub}
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.psi.{PsiManager, PsiFile}
import lang.psi.api.ScalaFile
import lang.psi.impl.ScalaPsiElementFactory
import decompiler.DecompilerUtil.DecompilationResult
import com.intellij.openapi.project.{DefaultProjectFactory, Project, ProjectManager}
import reflect.NameTransformer
import scala.annotation.tailrec
import java.io.IOException
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.StubVersion

/**
 * @author ilyas
 */

object ScClsStubBuilderFactory {
  def canBeProcessed(file: VirtualFile): Boolean = {
    try {
      canBeProcessed(file, file.contentsToByteArray())
    } catch {
      case ex: IOException => false
      case u: UnsupportedOperationException => false //why we need to handle this?
    }
  }
  
  def canBeProcessed(file: VirtualFile, bytes: => Array[Byte]): Boolean = {
    val name: String = file.getNameWithoutExtension
    if (name.contains("$")) {
      val parent: VirtualFile = file.getParent
      @tailrec
      def checkName(name: String): Boolean = {
        val child: VirtualFile = parent.findChild(name + ".class")
        if (child != null) {
          val res = DecompilerUtil.isScalaFile(child)
          if (res) return true //let's handle it separately to avoid giving it for Java.
        }
        val index = name.lastIndexOf("$")
        if (index == -1) return false
        var newName = name.substring(0, index)
        while (newName.endsWith("$")) newName = newName.dropRight(1)
        checkName(newName)
      }
      if (checkName(name)) return true
    }
    DecompilerUtil.isScalaFile(file, bytes)
  }
}

class ScClsStubBuilderFactory extends ClsStubBuilderFactory[ScalaFile] {
  override def getStubVersion: Int = StubVersion.STUB_VERSION
  
  def buildFileStub(vFile: VirtualFile, bytes: Array[Byte]): PsiFileStub[ScalaFile] = {
    buildFileStub(vFile, bytes, ProjectManager.getInstance().getDefaultProject)
  }

  override def buildFileStub(vFile: VirtualFile, bytes: Array[Byte], project: Project): PsiFileStub[ScalaFile] = {
    val DecompilationResult(_, source, text, _) = DecompilerUtil.decompile(vFile, bytes)
    val file = ScalaPsiElementFactory.createScalaFile(text.replace("\r", ""),
      PsiManager.getInstance(DefaultProjectFactory.getInstance().getDefaultProject))

    val adj = file.asInstanceOf[CompiledFileAdjuster]
    adj.setCompiled(c = true)
    adj.setSourceFileName(source)
    adj.setVirtualFile(vFile)

    val fType = LanguageParserDefinitions.INSTANCE.forLanguage(ScalaFileType.SCALA_LANGUAGE).getFileNodeType
    val stub = fType.asInstanceOf[IStubFileElementType[PsiFileStub[PsiFile]]].getBuilder.buildStubTree(file)
    stub.asInstanceOf[PsiFileStubImpl[PsiFile]].clearPsi("Stub was built from decompiled file")
    stub.asInstanceOf[PsiFileStub[ScalaFile]]
  }

  def canBeProcessed(file: VirtualFile, bytes: Array[Byte]): Boolean = {
    ScClsStubBuilderFactory.canBeProcessed(file, bytes)
  }

  def isInnerClass(file: VirtualFile): Boolean = false
}
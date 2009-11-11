package org.jetbrains.plugins.scala
package decompiler

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.compiled.ClsStubBuilderFactory

import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.stubs.{PsiFileStubImpl, StubTree, PsiFileStub}
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.psi.{PsiManager, PsiElement, PsiFile}
import lang.psi.api.ScalaFile
import lang.psi.impl.ScalaPsiElementFactory

/**
 * @author ilyas
 */

class ScClsStubBuilderFactory extends ClsStubBuilderFactory[ScalaFile] {
  def buildFileStub(vFile: VirtualFile, bytes: Array[Byte]): PsiFileStub[ScalaFile] = {
    val (text, source) = DecompilerUtil.decompile(bytes, vFile)
    val file = ScalaPsiElementFactory.createScalaFile(text.replace("\r", ""), PsiManager.getInstance(DecompilerUtil.obtainProject))
    
    val adj = file.asInstanceOf[CompiledFileAdjuster]
    adj.setCompiled(true)
    adj.setSourceFileName(source)
    adj.setVirtualFile(vFile)

    val fType = LanguageParserDefinitions.INSTANCE.forLanguage(ScalaFileType.SCALA_LANGUAGE).getFileNodeType()
    val stub = fType.asInstanceOf[IStubFileElementType[PsiFileStub[PsiFile]]].getBuilder().buildStubTree(file)
    stub.asInstanceOf[PsiFileStubImpl[PsiFile]].setPsi(null)
    return stub.asInstanceOf[PsiFileStub[ScalaFile]]
  }

  def canBeProcessed(file: VirtualFile, bytes: Array[Byte]) = DecompilerUtil.isScalaFile(file, bytes)
}
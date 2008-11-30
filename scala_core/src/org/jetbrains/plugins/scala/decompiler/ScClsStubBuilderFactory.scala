package org.jetbrains.plugins.scala.decompiler

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.compiled.ClsStubBuilderFactory

import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.psi.{PsiManager, PsiElement, PsiFile}
import lang.psi.api.ScalaFile
import lang.psi.impl.ScalaPsiElementFactory
import lang.psi.stubs.ScalaFileStubBuilder

/**
 * @author ilyas
 */

class ScClsStubBuilderFactory extends ClsStubBuilderFactory[ScalaFile] {

  def buildFileStub(vFile: VirtualFile, bytes: Array[Byte]): PsiFileStub[ScalaFile] = {
    val text = DecompilerUtil.decompile(bytes, vFile)._1
    val file = ScalaPsiElementFactory.createScalaFile(text, PsiManager.getInstance(DecompilerUtil.obtainProject))
    val fType = LanguageParserDefinitions.INSTANCE.forLanguage(ScalaFileType.SCALA_LANGUAGE).getFileNodeType()
    val stub = fType.asInstanceOf[IStubFileElementType[PsiFileStub[PsiFile]]].getBuilder().buildStubTree(file)
    return stub.asInstanceOf[PsiFileStub[ScalaFile]]
  }

  def canBeProcessed(file: VirtualFile, bytes: Array[Byte]) = DecompilerUtil.isScalaFile(file, bytes)
}
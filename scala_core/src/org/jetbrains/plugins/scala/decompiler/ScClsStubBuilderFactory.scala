package org.jetbrains.plugins.scala.decompiler

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.compiled.ClsStubBuilderFactory

import com.intellij.psi.PsiManager
import com.intellij.psi.stubs.PsiFileStub
import lang.psi.api.ScalaFile
import lang.psi.impl.ScalaPsiElementFactory
import lang.psi.stubs.ScalaFileStubBuilder

/**
 * @author ilyas
 */

class ScClsStubBuilderFactory extends ClsStubBuilderFactory[ScalaFile] {


  def buildFileStub(vFile: VirtualFile, bytes: Array[Byte]): PsiFileStub[ScalaFile] = {
    val text = DecompilerUtil.decompile(bytes)
    val file = ScalaPsiElementFactory.createScalaFile(text, PsiManager.getInstance(DecompilerUtil.obtainProject))
    new ScalaFileStubBuilder().createStubForFile(file)
  }

  def canBeProcessed(file: VirtualFile, bytes: Array[Byte]) = {
    ScalaDecompiler.isScalaFile(bytes)
  }
}
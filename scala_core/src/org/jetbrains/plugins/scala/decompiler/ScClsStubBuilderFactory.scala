package org.jetbrains.plugins.scala.decompiler

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.compiled.ClsStubBuilderFactory
import com.intellij.psi.stubs.PsiFileStub
import lang.psi.api.ScalaFile

/**
 * @author ilyas
 */

class ScClsStubBuilderFactory extends ClsStubBuilderFactory[ScalaFile] {


  def buildFileStub(file: VirtualFile, bytes: Array[Byte]): PsiFileStub[ScalaFile] = {
    val fileStub: PsiFileStub[ScalaFile] = ScalaDecompiler.createFileStub(file, bytes);
    fileStub
  }

  def canBeProcessed(file: VirtualFile, bytes: Array[Byte]) = {
    ScalaDecompiler.isScalaFile(bytes)
  }
}
package org.jetbrains.plugins.scala
package decompiler

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.compiled.{ClassFileDecompilers, ClsStubBuilder}

class ScClassFileDecompiler extends ClassFileDecompilers.Full {

  override def accepts(file: VirtualFile): Boolean = ScClsStubBuilder.canBeProcessed(file)

  override val getStubBuilder: ClsStubBuilder = new ScClsStubBuilder

  override def createFileViewProvider(file: VirtualFile, manager: PsiManager, physical: Boolean) =
    new ScClassFileViewProvider(manager, file, physical, DecompilerUtil.isScalaFile(file))

}

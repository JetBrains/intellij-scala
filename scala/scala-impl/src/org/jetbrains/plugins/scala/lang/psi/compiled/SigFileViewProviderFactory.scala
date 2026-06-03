package org.jetbrains.plugins.scala.lang.psi.compiled

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.{BinaryFileDecompiler, FileType}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.compiled.ClassFileStubBuilder
import com.intellij.psi.{FileViewProvider, FileViewProviderFactory, PsiManager}
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaLanguage}

import javax.swing._

/**
 * Scala signatures which are usually stored in .class files, as a separate files
 * https://github.com/scala/scala/pull/7712
 * */
object SigFileType extends FileType {
  @NonNls
  override def getName = "SIG"

  override def getDescription: String = ScalaBundle.message("file.type.scala.outlines")

  override def getDefaultExtension = "sig"

  override def getIcon: Icon = null

  override def isBinary = true

  override def isReadOnly = true

  override def getCharset(file: VirtualFile, content: Array[Byte]): String = null
}


class SigFileViewProviderFactory extends FileViewProviderFactory {
  override def createFileViewProvider(file: VirtualFile,
                                      language: Language,
                                      manager: PsiManager,
                                      eventSystemEnabled: Boolean): FileViewProvider = {

    ScClassFileDecompiler.createFileViewProviderImpl(manager, file, eventSystemEnabled, ScalaLanguage.INSTANCE)
  }
}

class SigFileStubBuilder extends ClassFileStubBuilder

class SigFileDecompiler extends BinaryFileDecompiler {
  override def decompile(file: VirtualFile): CharSequence = {
    DecompilationResult.sourceNameAndText(file).map(_._2).getOrElse(ScalaBundle.message("could.not.decompile.file.comment", file.getName))
  }
}
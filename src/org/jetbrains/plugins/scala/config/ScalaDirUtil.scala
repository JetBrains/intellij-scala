package org.jetbrains.plugins.scala.config

import com.intellij.openapi.progress.{ProgressManager, ProgressIndicator}
import com.intellij.util.text.CharArrayCharSequence
import com.intellij.openapi.util.io.FileUtil
import com.intellij.lexer.Lexer
import com.intellij.util.StringBuilderSpinAllocator
import com.intellij.openapi.util.text.StringUtil
import java.util.{List, ArrayList}
import com.intellij.openapi.fileTypes.{LanguageFileType, FileType, FileTypeManager}
import com.intellij.openapi.util.{Pair, SystemInfo}
import java.io.{IOException, File}
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenTypes, ScalaLexer}
import java.lang.StringBuilder

/**
 * @author Alexander Podkhalyuzin
 */
object ScalaDirUtil {
  //todo: that is also copy/paste from JavaUtil. Changes here only in getPackageStatement
  def suggestRoots(dir: File, fileType: LanguageFileType): List[Pair[File, String]] = {
    val foundDirectories: ArrayList[Pair[File, String]] = new ArrayList[Pair[File, String]]
    try {
      suggestRootsImpl(dir, dir, foundDirectories, fileType)
    }
    catch {
      case ignore: PathFoundException =>
    }
    foundDirectories
  }

  private def suggestRootsImpl(base: File, dir: File, foundDirectories: ArrayList[_ >: Pair[File, String]],
                               fileType: LanguageFileType) {
    if (!dir.isDirectory) {
      return
    }
    val typeManager: FileTypeManager = FileTypeManager.getInstance
    if (typeManager.isFileIgnored(dir.getName)) {
      return
    }
    val progressIndicator: ProgressIndicator = ProgressManager.getInstance.getProgressIndicator
    if (progressIndicator != null) {
      if (progressIndicator.isCanceled) {
        return
      }
      progressIndicator.setText2(dir.getPath)
    }
    val list: Array[File] = dir.listFiles
    if (list == null || list.length == 0) {
      return
    }
    for (child <- list) {
      if (child.isFile) {
        val tp: FileType = typeManager.getFileTypeByFileName(child.getName)
        if (fileType eq tp) {
          if (progressIndicator != null && progressIndicator.isCanceled) {
            return
          }
          val root: Pair[File, String] = suggestRootForScalaFile(child)
          if (root != null) {
            val packagePrefix: String = getPackagePrefix(base, root)
            if (packagePrefix == null) {
              foundDirectories.add(root)
            }
            else {
              foundDirectories.add(Pair.create(base, packagePrefix))
            }
            throw new PathFoundException(root.getFirst)
          }
          else {
            return
          }
        }
      }
    }
    for (child <- list) {
      if (child.isDirectory) {
        try {
          suggestRootsImpl(base, child, foundDirectories, fileType)
        }
        catch {
          case found: PathFoundException => {
            if (!(found.myDirectory == child)) {
              throw found
            }
          }
        }
      }
    }
  }

  private def getPackagePrefix(base: File, root: Pair[File, String]): String = {
    var result: String = ""
    var parent: File = base
    while (parent != null) {
      if (parent == root.getFirst) {
        return root.getSecond + (if (root.getSecond.length > 0 && result.length > 0) "." else "") + result
      }
      result = parent.getName + (if (result.length > 0) "." else "") + result
      parent = parent.getParentFile
    }
    null
  }

  private def suggestRootForScalaFile(scalaFile: File): Pair[File, String] = {
    if (!scalaFile.isFile) return null
    var chars: CharSequence = null
    try {
      chars = new CharArrayCharSequence(FileUtil.loadFileText(scalaFile): _*)
    }
    catch {
      case e: IOException => {
        return null
      }
    }
    val packageName: String = getPackageStatement(chars)
    if (packageName != null) {
      var root: File = scalaFile.getParentFile
      var index: Int = packageName.length
      while (index > 0) {
        val index1: Int = packageName.lastIndexOf('.', index - 1)
        val token: String = packageName.substring(index1 + 1, index)
        val dirName: String = root.getName
        val equalsToToken: Boolean = if (SystemInfo.isFileSystemCaseSensitive) (dirName == token) else dirName.equalsIgnoreCase(token)
        if (!equalsToToken) {
          return Pair.create(root, packageName.substring(0, index))
        }
        val parent: String = root.getParent
        if (parent == null) {
          return null
        }
        root = new File(parent)
        index = index1
      }
      return Pair.create(root, "")
    }
    null
  }

  def getPackageStatement(text: CharSequence): String = {
    val lexer: Lexer = new ScalaLexer
    lexer.start(text)
    val buffer: StringBuilder = StringBuilderSpinAllocator.alloc()
    def readPackage(firstTime: Boolean) {
      skipWhiteSpaceAndComments(lexer)
      if (lexer.getTokenType != ScalaTokenTypes.kPACKAGE) return
      if (!firstTime) buffer.append('.')
      lexer.advance()
      skipWhiteSpaceAndComments(lexer)
      if (lexer.getTokenType == ScalaTokenTypes.kOBJECT) {
        lexer.advance()
        skipWhiteSpaceAndComments(lexer)
        if (lexer.getTokenType == ScalaTokenTypes.tIDENTIFIER)
          buffer.append(text, lexer.getTokenStart, lexer.getTokenEnd)
        return
      }
      def appendPackageStatement() {
        while (true) {
          if (lexer.getTokenType != ScalaTokenTypes.tIDENTIFIER) return
          buffer.append(text, lexer.getTokenStart, lexer.getTokenEnd)
          lexer.advance()
          skipWhiteSpaceAndComments(lexer)
          if (lexer.getTokenType != ScalaTokenTypes.tDOT) return
          buffer.append('.')
          lexer.advance()
          skipWhiteSpaceAndComments(lexer)
        }
      }
      appendPackageStatement()
      if (lexer.getTokenType == ScalaTokenTypes.tLBRACE) {
        lexer.advance()
        skipWhiteSpaceAndComments(lexer)
      }
      readPackage(false)
    }
    try {
      readPackage(true)
      val packageName: String = buffer.toString
      if (packageName.length == 0 || StringUtil.endsWithChar(packageName, '.')) return null
      packageName
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer)
    }
  }

  def skipWhiteSpaceAndComments(lexer: Lexer) {
    while (ScalaTokenTypes.WHITES_SPACES_AND_COMMENTS_TOKEN_SET.contains(lexer.getTokenType)) {
      lexer.advance()
    }
  }

  private class PathFoundException(val myDirectory: File) extends Exception
}


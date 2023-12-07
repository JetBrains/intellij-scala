package org.jetbrains.plugins.scala.decompileToJava

import com.intellij.application.options.CodeStyle
import com.intellij.ide.highlighter.{JavaClassFileType, JavaFileType}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.java.decompiler.IdeaLogger
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler
import org.jetbrains.java.decompiler.main.extern.{IBytecodeProvider, IFernflowerPreferences, IResultSaver}
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.psi.api.ScFile

import java.io.File
import java.util.jar.Manifest
import scala.collection.mutable
import scala.jdk.CollectionConverters.MapHasAsJava
import scala.util.{Failure, Success, Try}

private class ScalaDecompilerServiceImpl extends ScalaDecompilerService {

  import ScalaDecompilerServiceImpl._

  override def decompile(file: ScFile): Try[String] = if (file.isCompiled) {
    try {
      val mappings = inReadAction {
        mappingsForClassfile(file.getVirtualFile)
      }
      val saver = new ScalaResultSaver

      val provider: IBytecodeProvider = (externalPath, _) => {
        val path = new File(FileUtil.toSystemIndependentName(externalPath))
        mappings.get(path).map(_.apply()).orNull
      }
      val options: Map[String, AnyRef] = getFernflowerDecompilerOptions
      val decompiler = new BaseDecompiler(provider, saver, options.asJava, new IdeaLogger())
      mappings.foreach { case (path, _) => decompiler.addSource(path) }
      decompiler.decompileContext()
      Success(saver.result)
    } catch {
      case e: IdeaLogger.InternalException =>
        Failure(e)
    }
  }
  else Failure(new RuntimeException(s"Unable to decompile ${file.getName}"))

  private[this] def isClassGeneratedFrom(sourceName: String, classfile: VirtualFile): Boolean =
    classfile.getFileType == JavaClassFileType.INSTANCE && {
      val name = classfile.getNameWithoutExtension
      name == sourceName || name.startsWith(sourceName + "$")
    }

  private[this] def mappingsForClassfile(file: VirtualFile): Map[File, FileContents] =
    file.getParent.getChildren.iterator.collect {
      case child if isClassGeneratedFrom(file.getNameWithoutExtension, child) =>
        new File(child.getPath) -> getFileContents(child)
    }.toMap
}

object ScalaDecompilerServiceImpl {
  type FileContents = () => Array[Byte]

  /**
   *  Original text inspired by [[org.jetbrains.java.decompiler.IdeaDecompilerKt.IDEA_DECOMPILER_BANNER]].<br>
   *  We use custom text because the result of compilation is concatenation of multiple compilation results, not a single `.class` file
   *  Original wording was "recreated from a .class file by IntelliJ IDEA"
   */
  private val Banner =
    """//
      |// Source code recreated by IntelliJ IDEA
      |// (powered by FernFlower decompiler)
      |//
      |""".stripMargin.replace("\r", "")

  /**
   * @note for options used for Java .class files decompiler see:<br>
   *       [[org.jetbrains.java.decompiler.IdeaDecompilerKt.getOptions]]<br>
   *       Some of the option values were taken from there
   */
  private def getFernflowerDecompilerOptions: Map[String, AnyRef] = {
    val options = CodeStyle.getDefaultSettings.getIndentOptions(JavaFileType.INSTANCE)
    val indent = " " * options.INDENT_SIZE

    val DISABLED = "0"
    val ENABLED = "1"

    Map(
      IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR -> DISABLED,
      IFernflowerPreferences.INDENT_STRING -> indent,

      //I didn't find the original reasoning why it's disabled, but I guess in Scala it might be useful to see more internals
      IFernflowerPreferences.REMOVE_BRIDGE -> DISABLED,
      //I guess the same reasoning as with `REMOVE_BRIDGE`
      IFernflowerPreferences.REMOVE_SYNTHETIC -> DISABLED,

      //This option is needed in order it works on Windows (otherwise the result will contain `\r` and document.setText won't work)
      IFernflowerPreferences.NEW_LINE_SEPARATOR -> ENABLED,

      //example: show `Option<String> foo()` instead of `Option foo()`
      IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES -> ENABLED,

      //avoid very long decompilation
      //value is copied from IdeaDecompilerKt
      IFernflowerPreferences.MAX_PROCESSING_METHOD -> 60.asInstanceOf[java.lang.Integer],

      //value is copied from IdeaDecompilerKt
      IFernflowerPreferences.IGNORE_INVALID_BYTECODE -> ENABLED,

      //Skipping the BANNER - we will append the banner manually after concatenating all decompilation results for a given file
      //IFernflowerPreferences.BANNER -> ScalaDecompilerServiceImpl.BANNER,

      //it's used in IdeaDecompilerKt, but I have no idea what it affects from the user perspective
      //IFernflowerPreferences.VERIFY_ANONYMOUS_CLASSES -> ENABLED,
    )
  }

  private def getFileContents(vf: VirtualFile): FileContents =
    () => vf.contentsToByteArray(false)

  private class ScalaResultSaver extends IResultSaver {
    private val fileNamesWithDecompiledText: mutable.Buffer[(String, String)] =
      mutable.ArrayBuffer.empty

    def result: String = {
      val concatenatedResults = fileNamesWithDecompiledText
        .map { case (fileName, decompiledText) =>
          s"//decompiled from ${fileName.stripSuffix(".java")}.class\n" +
            decompiledText
        }
        .mkString("\n")
      Banner + "\n" + concatenatedResults
    }

    override def saveClassFile(path: String, qname: String, entry: String, content: String, mapping: Array[Int]): Unit =
      if (entry != null && content != null) {
        fileNamesWithDecompiledText += entry -> content
      }

    override def saveClassEntry(path: String, archive: String, qname: String, entry: String, content: String): Unit = ()
    override def saveDirEntry(path: String, archive: String, entry: String): Unit = ()
    override def closeArchive(path: String, name: String): Unit = ()
    override def saveFolder(path: String): Unit = ()
    override def copyFile(source: String, path: String, entry: String): Unit = ()

    override def createArchive(path: String, name: String, manifest: Manifest): Unit = ()
    override def copyEntry(source: String, path: String, name: String, entry: String): Unit = ()
  }
}

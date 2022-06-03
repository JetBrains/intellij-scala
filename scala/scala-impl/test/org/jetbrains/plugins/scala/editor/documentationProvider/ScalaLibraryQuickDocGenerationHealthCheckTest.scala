package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import com.intellij.psi.{PsiComment, PsiManager, PsiNamedElement}
import org.jetbrains.plugins.scala.base.libraryLoaders.ScalaSDKLoader
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocContentGenerator.UnresolvedMacroInfo
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaLibraryQuickDocGenerationHealthCheckTest.{KnownProblem, relativeFilePath}
import org.jetbrains.plugins.scala.extensions.{ArrayExt, PsiElementExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDocCommentOwner
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaVersion, base}
import org.junit.Assert

import scala.util.Try

class ScalaLibraryQuickDocGenerationHealthCheckTest extends base.ScalaLightCodeInsightFixtureTestAdapter {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  def testAllMacroAreResolved(): Unit = {
    ScalaDocContentGenerator.unresolvedMacro.clear()

    val scalaSdkLoader = librariesLoaders.toArray.findByType[ScalaSDKLoader].get
    val scalaLibrarySourcesRoot = scalaSdkLoader.sourceRoot

    VfsUtilCore.processFilesRecursively(
      scalaLibrarySourcesRoot,
      (file: VirtualFile) => {
        //println(s"processing: ${relativeFilePath(file)}")
        generateAllDocs(file)
        true
      }
    )

    val unresolvedMacro = ScalaDocContentGenerator.unresolvedMacro
    if (unresolvedMacro.nonEmpty) {
      val details = unresolvedMacro
        .map { case UnresolvedMacroInfo(file, commentOwnerName, macroKey) =>
          s"symbol: $commentOwnerName  macro: $macroKey file: ${relativeFilePath(file)}"
        }
        .mkString("\n")
      Assert.fail(s"Unresolved macro detected during scaladoc generation for scala library ${version.minor}:\n$details")
    }
  }

  private def generateAllDocs(file: VirtualFile): Unit =
    file.getFileType match {
      case ScalaFileType.INSTANCE =>
        val psiFile = PsiManager.getInstance(getProject).findFile(file).asInstanceOf[ScalaFile]
        val document = FileDocumentManager.getInstance().getDocument(file)
        generateAllDocs(psiFile, document)
      case _ =>
    }

  private def generateAllDocs(psiFile: ScalaFile, document: Document): Unit = {
    val provider = new ScalaDocumentationProvider
    val docCommentOwners = collectDocComments(psiFile)
    for { owner <- docCommentOwners }
      Try(provider.generateDoc(owner, null)).recover {
        case ex if Option(ex.getMessage).getOrElse("").contains("Tree access disabled") =>
          // ignore known AST loading exceptions
          //  TODO: remove after fixing SCL-17792
          owner match {
            case named: PsiNamedElement =>
              val lineIdx = document.getLineNumber(owner.startOffset)
              System.err.println(s"$lineIdx: ${named.name}")
            case _  =>
          }
      }
  }

  private def collectDocComments(file: ScalaFile): Seq[ScDocCommentOwner] =
    file
      .breadthFirst {
        case _: PsiComment => false
        case _ => true
      }
      .collect {
        case docOwner: ScDocCommentOwner => docOwner
      }
      .toSeq
}

object ScalaLibraryQuickDocGenerationHealthCheckTest {
  private case class KnownProblem(fileRelativePath: String, commentOwnerName: String, macroKey: String)
  private object KnownProblem {
    def apply(unresolvedMacro: UnresolvedMacroInfo): KnownProblem = {
      val UnresolvedMacroInfo(file: VirtualFile, commentOwnerName: String, macroKey: String) = unresolvedMacro
      new KnownProblem(relativeFilePath(file), commentOwnerName, macroKey)
    }
  }

  private def relativeFilePath(file: VirtualFile): String = {
    val filePath = file.getPath
    val splitAt = "jar!/"
    filePath.substring(filePath.indexOf(splitAt) + splitAt.length).replace("\\", "/")
  }
}

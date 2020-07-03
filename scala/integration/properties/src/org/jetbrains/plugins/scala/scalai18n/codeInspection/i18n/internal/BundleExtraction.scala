package org.jetbrains.plugins.scala
package scalai18n
package codeInspection
package i18n
package internal

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages.InputDialog
import com.intellij.openapi.ui.{InputValidatorEx, Messages}
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.{inWriteAction, _}
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent.{BundleInfo, BundleUsageInfo, Entry}

object BundleExtraction {


  case class BundleExtractionInfo(bundleClassName: String, bundleQualifiedClassName: String, key: String, arguments: Seq[String])

  //noinspection ScalaExtractStringToBundle
  def executeBundleExtraction(element: PsiElement, parts: Seq[ExtractPart], project: Project)(f: BundleExtractionInfo => Unit): Unit = {
    val showErrorDialog = Messages.showErrorDialog(project, _: String, _: String)
    val BundleUsageInfo(elementPath, srcRoot, _, maybeBundlePath) =
      I18nBundleContent
        .findBundlePathFor(element)
        .getOrElse {
          val elementPath = element.containingVirtualFile.fold("<memory-only file>")(_.getCanonicalPath)
          showErrorDialog(s"Couldn't determine module root for element in $elementPath", "No module root found")
          return
        }

    assert(elementPath.startsWith(srcRoot))

    val BundleInfo(bundlePropertyPath, _, bundleClassName, bundleQualifiedClassName) =
      maybeBundlePath
        .getOrElse {
          showErrorDialog("Couldn't find bundle for " + elementPath, "No bundle found")
          return
        }

    val fileUrl = VirtualFileManager.constructUrl(URLUtil.FILE_PROTOCOL, bundlePropertyPath)
    val vfile = VirtualFileManager.getInstance().findFileByUrl(fileUrl)

    if (vfile == null) {
      throw new Exception(s"Failed to open bundle file at $bundlePropertyPath")
    }

    // save file that may already been loaded
    val fileDocumentManager = FileDocumentManager.getInstance()
    fileDocumentManager
      .getCachedDocument(vfile).nullSafe
      .foreach(fileDocumentManager.saveDocument)


    val bundle = I18nBundleContent.read(bundlePropertyPath)
    val path = elementPath.substring(srcRoot.length)
    val (keyProposal, text, arguments) = toKeyAndTextAndArgs(parts)

    val inputDialog = new InputDialog(project, "Key:", "Enter key for new bundle entry", null, keyProposal, new BundleNameInputValidator(bundle))
    if (!inputDialog.showAndGet()) {
      return
    }

    val key = inputDialog.getInputString
    assert(key != null)

    val newEntry = Entry(key, text, path)

    inWriteAction {
      val outputStream = vfile.getOutputStream(this)
      try bundle
        .withEntry(newEntry)
        .writeTo(outputStream)
      finally outputStream.close()
      val document = FileDocumentManager.getInstance().getDocument(vfile)

      f(BundleExtractionInfo(bundleClassName, bundleQualifiedClassName, key, arguments))
    }
  }

  private val lastwordRegex = raw"\w+".r
  private def lastWord(string: String): Option[String] =
    lastwordRegex.findAllIn(string).toSeq.lastOption

  private def toKeyAndTextAndArgs(parts: Seq[ExtractPart]): (String, String, Seq[String]) = {
    val argNums = Iterator.from(0)
    val bindings = parts.collect {
      case TextPart(s) => (s, s, None)
      case ExprPart(value) =>
        def keyAlternative = value.substring(0, value.length min 20)
        val argNum = argNums.next()
        (lastWord(value).getOrElse(keyAlternative), s"{$argNum}", Some(value))
    }
    val (keyParts, textParts, argumentsPerPart) = bindings.unzip3
    val arguments = argumentsPerPart.flatten
    val key = I18nBundleContent.convertStringToKey(keyParts.mkString("."))
    val text = I18nBundleContent.escapeText(textParts.mkString, arguments.nonEmpty)
    (key, text, arguments)
  }

  //noinspection ScalaExtractStringToBundle,ReferencePassedToNls
  private class BundleNameInputValidator(bundle: I18nBundleContent) extends InputValidatorEx {
    private val wrongRegex = raw"[^\w\.]+".r
    @Nullable
    override def getErrorText(inputString: String): String = inputString match {
      case "" => "Cannot be empty"
      case key if key.contains(" ") || key.contains("\t") => "Key cannot contain spaces"
      case key if wrongRegex.findAllMatchIn(key).nonEmpty => "Invalid characters: " + wrongRegex.findAllMatchIn(key).flatMap(_.toString()).toSet.mkString.sorted
      case key if bundle.hasKey(key) => "Key already in bundle: " + key
      case _ => null
    }

    override def checkInput(inputString: String): Boolean = getErrorText(inputString) == null
    override def canClose(inputString: String): Boolean = checkInput(inputString)
  }
}

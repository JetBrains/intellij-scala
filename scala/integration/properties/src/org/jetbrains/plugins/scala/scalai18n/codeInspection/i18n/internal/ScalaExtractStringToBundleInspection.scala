package org.jetbrains.plugins.scala
package scalai18n
package codeInspection
package i18n
package internal

import java.util

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.{BatchQuickFix, InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.Messages.InputDialog
import com.intellij.openapi.ui.{InputValidatorEx, Messages}
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractRegisteredInspection}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.format._
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotationsHolder, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.internal.ScalaExtractStringToBundleInspection._
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent.{BundleInfo, BundleUsageInfo, Entry}

//noinspection ScalaExtractStringToBundle
class ScalaExtractStringToBundleInspection extends AbstractRegisteredInspection {

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix] = None,
                                           descriptionTemplate: String = getDisplayName,
                                           highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    element match {
      case element@TopmostStringParts(parts) if !shouldBeIgnored(element, parts) =>
        val isNaturalLang = containsNaturalLangString(element, parts)

        if (isNaturalLang || (isOnTheFly && !ApplicationManager.getApplication.isUnitTestMode )) {
          val quickFixes = Array[LocalQuickFix](new MoveToBundleQuickFix(element)) ++ maybeQuickFix
          val highlight =
            if (isNaturalLang) highlightType
            else ProblemHighlightType.INFORMATION // make quickfix available for all strings
          Some(manager.createProblemDescriptor(element, descriptionTemplate, isOnTheFly, quickFixes, highlight))
        } else None
      case _ => None
    }
  }
}

//noinspection ScalaExtractStringToBundle
object ScalaExtractStringToBundleInspection {
  private def containsNaturalLangString(element: PsiElement, parts: Seq[StringPart]): Boolean =
    parts.exists {
      case Text(s) => isNaturalLangString(element, s)
      case _ => false
    }

  private def isNaturalLangString(element: PsiElement, string: String): Boolean =
    isPassedToNls(element)
    //string.length > 3 &&
    //hasAtLeastOneLetters(string) &&
    //!hasCamelCase(string)

  private def isPassedToNls(element: PsiElement): Boolean =
    ScalaI18nUtil.isPassedToAnnotated(element, AnnotationUtil.NLS)

  private lazy val letterRegex = raw"""\w""".r
  private def hasAtLeastOneLetters(string: String): Boolean =
    letterRegex.findFirstIn(string).isDefined

  private lazy val camelCaseRegex = raw"""\p{Lower}\p{Upper}""".r
  private def hasCamelCase(string: String): Boolean =
    camelCaseRegex.findFirstIn(string).isDefined

  private def shouldBeIgnored(element: PsiElement, parts: Seq[StringPart]): Boolean =
    hasNonNLSAnnotation(element) ||
      isTestSource(element) ||
      isInBundleMessageCall(element)

  private def hasNonNLSAnnotation(element: PsiElement): Boolean =
    element
      .withParents
      .collect { case holder: ScAnnotationsHolder => holder }
      .exists(_.hasAnnotation(AnnotationUtil.NON_NLS))

  private def isTestSource(element: PsiElement): Boolean = {
    //element.getContainingFile.toOption.exists(file => TestSourcesFilter.isTestSources(file.getVirtualFile, element.getProject))
    element.getContainingFile.toOption.exists(file => ProjectFileIndex.getInstance(element.getProject).isInTestSourceContent(file.getVirtualFile))
  }

  private def isInBundleMessageCall(element: PsiElement): Boolean =
    element.asOptionOf[ScLiteral].exists(ScalaI18nUtil.mustBePropertyKey(_))

  private object TopmostStringParts {
    def unapply(expr: ScExpression): Option[Seq[StringPart]] = {
      def parentIsStringConcatenationOrFormat = expr.parent.exists {
        case StringConcatenationExpression(_, _) => true
        case p => FormattedStringParser.parse(p).isDefined
      }
      AnyStringParser.parse(expr).filterNot(_ => parentIsStringConcatenationOrFormat)
    }
  }

  private class MoveToBundleQuickFix(_element: ScExpression)
      extends AbstractFixOnPsiElement("Extract to bundle", _element) with BatchQuickFix[Null] {
    override def startInWriteAction(): Boolean = false
    override protected def doApplyFix(element: ScExpression)(implicit project: Project): Unit = {
      val parts = element match {
        case TopmostStringParts(parts) => parts
        case _ => return
      }

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
        PsiDocumentManager.getInstance(project).commitDocument(document)
        // TODO: make undo working
        //CommandProcessor.getInstance().addAffectedDocuments(project, document);

        // add import
        val importsHolder: ScImportsHolder =
          Option(PsiTreeUtil.getParentOfType(element, classOf[ScPackaging]))
            .getOrElse(element.getContainingFile.asInstanceOf[ScImportsHolder])
        importsHolder.addImportForPath(bundleQualifiedClassName)

        // replace string with message call
        val argString =
          if (arguments.isEmpty) ""
          else arguments.mkString(", ", ", ", "")
        _element.replace(ScalaPsiElementFactory.createExpressionFromText(
          s"""$bundleClassName.message("$key"$argString)"""
        ))
      }
    }

    private val lastwordRegex = raw"\w+".r
    private def lastWord(string: String): Option[String] =
      lastwordRegex.findAllIn(string).toSeq.lastOption


    def toKeyAndTextAndArgs(parts: Seq[StringPart]): (String, String, Seq[String]) = {
      val argNums = Iterator.from(0)
      val bindings = parts.collect {
        case Text(s) => (s, s, None)
        case injection: Injection =>
          val value = injection.value
          def keyAlternative = value.substring(0, value.length min 20)
          val argNum = argNums.next()
          (lastWord(value).getOrElse(keyAlternative), s"{$argNum}", Some(value))
      }
      val (keyParts, textParts, arguments) = bindings.unzip3
      val key = I18nBundleContent.convertStringToKey(keyParts.mkString("."))
      val text = I18nBundleContent.escapeText(textParts.mkString, arguments.nonEmpty)
      (key, text, arguments.flatten)
    }

    // find a way to disable batch mode... until then just show this message
    override def applyFix(project: Project, descriptors: Array[Null], psiElementsToIgnore: util.List[PsiElement], refreshViews: Runnable): Unit =
      Messages.showErrorDialog(project, "Don' run ExtractStringToBundle inspection in batch mode", "Do not run in batch mode")
  }

  class BundleNameInputValidator(bundle: I18nBundleContent) extends InputValidatorEx {
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
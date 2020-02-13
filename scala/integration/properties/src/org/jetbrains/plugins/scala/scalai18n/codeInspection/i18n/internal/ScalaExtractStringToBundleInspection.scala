package org.jetbrains.plugins.scala
package scalai18n
package codeInspection
package i18n
package internal

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.io.URLUtil
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractRegisteredInspection}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.format._
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotationsHolder, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.internal.ScalaExtractStringToBundleInspection._
import org.jetbrains.plugins.scala.util.internal.I18nStringBundle
import org.jetbrains.plugins.scala.util.internal.I18nStringBundle.{BundleInfo, BundleUsageInfo, Entry}

import scala.util.matching.Regex

class ScalaExtractStringToBundleInspection extends AbstractRegisteredInspection {

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix] = None,
                                           descriptionTemplate: String = getDisplayName,
                                           highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    Option(element) collect {
      case element@TopmostStringParts(parts) if containsNaturalLangString(parts) && !shouldBeIgnored(element, parts) =>
        val quickFixes = Array[LocalQuickFix](new MoveToBundleQuickFix(element)) ++ maybeQuickFix
        manager.createProblemDescriptor(element, descriptionTemplate, isOnTheFly, quickFixes, highlightType)
    }
  }
}

object ScalaExtractStringToBundleInspection {
  private def containsNaturalLangString(parts: Seq[StringPart]): Boolean =
    parts.exists {
      case Text(s) => isNaturalLangString(s)
      case _ => false
    }

  private def isNaturalLangString(string: String): Boolean =
    //string.length > 3 &&
    !hasCamelCase(string)

  lazy val camelCaseRegex: Regex = raw"""\p{Lower}\p{Upper}""".r
  def hasCamelCase(string: String): Boolean =
    camelCaseRegex.findFirstIn(string).isDefined

  private def shouldBeIgnored(element: PsiElement, parts: Seq[StringPart]): Boolean =
    hasNonNLSAnnotation(element) ||
      isTestSource(element) ||
      isInBundleMessageCall(element)

  private def hasNonNLSAnnotation(element: PsiElement): Boolean =
    element
      .withParents
      .collect { case holder: ScAnnotationsHolder => holder }
      .exists(_.hasAnnotation("org.jetbrains.annotations.NonNls"))

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

  private class MoveToBundleQuickFix(_element: ScExpression) extends AbstractFixOnPsiElement("Extract to bundle", _element) {
    override protected def doApplyFix(element: ScExpression)(implicit project: Project): Unit = {
      val parts = element match {
        case TopmostStringParts(parts) => parts
        case _ => return
      }

      val showErrorDialog = Messages.showErrorDialog(project, _: String, _: String)
      val BundleUsageInfo(elementPath, srcRoot, _, maybeBundlePath) =
        I18nStringBundle
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
            showErrorDialog("Couldn't find bundle in " + srcRoot, "No bundle found")
            return
          }

      val bundle = I18nStringBundle.readBundle(bundlePropertyPath)
      val path = elementPath.substring(srcRoot.length)
      val (key, text, arguments) = toKeyAndTextAndArgs(parts)
      val newEntry = Entry(key, text, path)

      // mark bundle file for undo
      val fileUrl = VirtualFileManager.constructUrl(URLUtil.FILE_PROTOCOL, bundlePropertyPath)
      val vfile = VirtualFileManager.getInstance().findFileByUrl(fileUrl)
      if (vfile == null) {
        throw new Exception(s"Failed to open bundle file at $bundlePropertyPath")
      }

      val outputStream = vfile.getOutputStream(this)
      try bundle
        .withEntry(newEntry)
        .writeTo(outputStream)
      finally outputStream.close()
      // TODO: make undo working
      //val document = FileDocumentManager.getInstance().getDocument(vfile)
      //CommandProcessor.getInstance().addAffectedDocuments(project, document);
      //PsiDocumentManager.getInstance(project).commitDocument(document)


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



    private val nonWordSeq = raw"\W+".r
    private val Trimmed = raw"\W*(.*?)\W*".r
    private def convertStringToKey(string: String): String = {
      val maxKeyLength = 60
      val Trimmed(fullKey) = nonWordSeq.replaceAllIn(string, ".")

      lazy val lastDotIdx = fullKey.lastIndexOf(".", maxKeyLength - 3)
      if (fullKey.length < maxKeyLength) fullKey
      else if (lastDotIdx < maxKeyLength - 20) fullKey.substring(0, maxKeyLength - 3) + "..."
      else fullKey.substring(0, lastDotIdx) + "..."
    }

    private val lastwordRegex = raw"\w+$$".r
    private def lastWord(string: String): Option[String] =
      lastwordRegex.findFirstIn(string)


    def toKeyAndTextAndArgs(parts: Seq[StringPart]): (String, String, Seq[String]) = {
      val argNums = Iterator.from(0)
      val bindings = parts.collect {
        case Text(s) => (s, s, None)
        case injection: Injection =>
          val value = injection.value
          def keyAlternative = value.substring(0, value.length max 20)
          val argNum = argNums.next()
          (lastWord(value).getOrElse(keyAlternative), s"{$argNum}", Some(value))
      }
      val (keyParts, textParts, arguments) = bindings.unzip3
      val key = convertStringToKey(keyParts.mkString("."))
      val text = {
        val text = textParts.mkString
        if (text.length > 100) text.replace("\n", "\\\n")
        else text.replace("\n", "\\n")
      }

      (key, text, arguments.flatten)
    }
  }
}
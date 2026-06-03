package org.jetbrains.plugins.scalaDirective.editor.copy

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.{Editor, RawText}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiComment, PsiElement, PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.editor.ScalaEditorUtils.findElementAtCaret_WithFixedEOFAndWhiteSpace
import org.jetbrains.plugins.scala.extensions.{&, ElementText, ObjectExt, Parent, PsiElementExt, invokeLater}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scalaDirective.dependencies.ScalaDirectiveDependencyKeys
import org.jetbrains.plugins.scalaDirective.editor.copy.UsingDirectiveDependencyCopyPastePreProcessor.{extractDependencyDirectivePrefix, parseSbtDependencies, scheduleCompletion}
import org.jetbrains.plugins.scalaDirective.lang.completion.{DirectivePrefix, UsingDirective}
import org.jetbrains.plugins.scalaDirective.lang.lexer.ScalaDirectiveTokenTypes
import org.jetbrains.plugins.scalaDirective.psi.api.ScDirective

final class UsingDirectiveDependencyCopyPastePreProcessor extends CopyPastePreProcessor {
  override def preprocessOnCopy(file: PsiFile, startOffsets: Array[Int], endOffsets: Array[Int], text: String): String = null

  override def preprocessOnPaste(project: Project, file: PsiFile, editor: Editor, text: String, rawText: RawText): String = {
    if (!file.is[ScalaFile]) return text

    val caret = editor.getCaretModel.getCurrentCaret
    val startOffset = caret.getSelectionStart
    val endOffset = caret.getSelectionEnd
    val elementAtCaret = findElementAtCaret_WithFixedEOFAndWhiteSpace(file, editor.getDocument, startOffset)

    extractDependencyDirectivePrefix(elementAtCaret, startOffset, endOffset) match {
      case None => text
      case Some(directivePrefix) =>
        val dependencies = parseSbtDependencies(text, elementAtCaret)
        if (dependencies.isEmpty) text
        else {
          val needsCompletion = dependencies.sizeIs == 1 && dependencies.head.versionOrPlaceholder.isRight
          dependencies.map { descriptor =>
            val newDelimiter = ":" * descriptor.groupDelimiterLength
            val prefix = s"${descriptor.groupId}$newDelimiter${descriptor.artifactId}:"
            descriptor.versionOrPlaceholder match {
              case Left(version) => s"$prefix$version"
              case Right(placeholder) =>
                if (needsCompletion) {
                  scheduleCompletion(project, editor, file)
                  prefix
                } else {
                  // TODO(SCL-23704): run template for values with placeholders if there are multiple dependencies?
                  s"$prefix$placeholder"
                }
            }
          }.mkString(directivePrefix, ", ", "")
        }
    }
  }
}

object UsingDirectiveDependencyCopyPastePreProcessor {
  private def scheduleCompletion(project: Project, editor: Editor, file: PsiFile): Unit = invokeLater {
    AutoPopupController.getInstance(project).scheduleAutoPopup(
      editor,
      CompletionType.BASIC,
      (_: PsiFile) == file
    )
  }

  private case class DependencyDescriptor(groupId: String, artifactId: String, versionOrPlaceholder: Either[String, String], groupDelimiterLength: Int)

  private def parseSbtDependencies(text: String, context: PsiElement): Seq[DependencyDescriptor] = {
    val element = ScalaPsiElementFactory.safe(_.createPsiElementFromText(text, context)(context.getProject))
    element match {
      case Some(SbtLibraryDependencies(descriptors@_*)) => descriptors
      case Some(SeqOfSbtDependencies(descriptors@_*)) => descriptors
      case Some(SbtDependency(descriptors)) => Seq(descriptors)
      case _ => Seq.empty
    }
  }

  // if there's no selection, then selectionStart and selectionEnd are equal
  private def extractDependencyDirectivePrefix(elementAtCaret: PsiElement,
                                               selectionStart: Int,
                                               selectionEnd: Int): Option[String] = {
    def isPastedToTheEndIgnoringWhitespaces(directive: ScDirective): Boolean =
      directive.getLastChild
        .withPrevSiblings.dropWhile(_.is[PsiWhiteSpace])
        .nextOption()
        .exists(_.endOffset <= selectionEnd)

    elementAtCaret match {
      case (comment: PsiComment) & ElementText(text) =>
        // empty directive, skip directive with unknown commands (i.e., those that cannot be parsed as ScDirective)
        val isApplicable = text.startsWith(DirectivePrefix) &&
          text.substring(DirectivePrefix.length).trim.isEmpty &&
          comment.startOffset + DirectivePrefix.length <= selectionStart

        Option.when(isApplicable)(s"$UsingDirective deps ")
      case Parent(directive: ScDirective) if isPastedToTheEndIgnoringWhitespaces(directive) =>
        def hasDependencyKey = directive.key.exists(key => ScalaDirectiveDependencyKeys.contains(key.getText))

        if (elementAtCaret.is[PsiWhiteSpace]) {
          elementAtCaret.prevSibling.map(_.elementType) match {
            // `//> ${caret}`
            case ScalaDirectiveTokenTypes.tDIRECTIVE_PREFIX => Some(s"$UsingDirective deps ")
            // `//> using ${caret}`
            case _ if directive.key.isEmpty => Some("deps ")
            // `//> using dep ${caret}`
            case _ => Option.when(hasDependencyKey)("")
          }
        }
        else if (directive.prefix == elementAtCaret) {
          // `//>${caret}` is ok, `//${caret}>` is not
          Option.unless(selectionStart < elementAtCaret.endOffset)(s"$UsingDirective deps ")
        }
        else if (directive.command == elementAtCaret) {
          // `//> ${caret}using ...`
          if (selectionStart == elementAtCaret.startOffset) {
            Some(s"$UsingDirective deps ")
          }
          // `//> using${caret} ...` - add whitespace right after the command
          else if (selectionStart == elementAtCaret.endOffset) {
            Some(" deps ")
          }
          else None // we might handle insert in the middle of the command later; for now ignore, paste as is
        }
        else if (directive.key.contains(elementAtCaret)) {
          // `//> using ${caret}dep ...`
          // or
          // `//> using ${caret}option ...`
          if (selectionStart == elementAtCaret.startOffset) {
            Some("deps ")
          }
          // `//> using dep${caret} ...` - add whitespace right after the key
          else if (selectionStart == elementAtCaret.endOffset && hasDependencyKey) {
            Some(" ")
          }
          else None // we might handle insert in the middle of the key later; for now ignore, paste as is
        }
        else if (elementAtCaret.elementType == ScalaDirectiveTokenTypes.tDIRECTIVE_COMMA) {
          val prefix =
            // `//> using dep foo:bar:baz${caret},` - add a comma before pasting a new dependency
            if (elementAtCaret.startOffset == selectionStart) ","
            // `//> using dep foo:bar:baz,${caret}`
            else ""
          Option.when(hasDependencyKey)(prefix)
        }
        else if (directive.value.contains(elementAtCaret)) {
          // `//> using dep ${caret}foo:bar:baz`
          // we might handle insert in the middle of the dependency later; for now ignore, paste as is
          Option.when(selectionStart == elementAtCaret.startOffset && hasDependencyKey)("")
        }
        else None
      case _ =>
        None
    }
  }

  private object SbtDependency {
    def unapply(element: PsiElement): Option[DependencyDescriptor] = element match {
      case ScInfixExpr(
        ScInfixExpr(groupId: ScStringLiteral, groupDelimiter@ElementText("%" | "%%"), artifactId: ScStringLiteral),
        ElementText("%"),
        versionElement@(_: ScStringLiteral | _: ScReferenceExpression)
      ) =>
        val versionOrPlaceholder = versionElement match {
          case version: ScStringLiteral => Left(version.getValue)
          case ref: ScReferenceExpression => Right(ref.refName)
        }

        Some(DependencyDescriptor(groupId.getValue, artifactId.getValue, versionOrPlaceholder, groupDelimiter.getTextLength))
      case _ => None
    }
  }

  private object SeqOfSbtDependencies {
    def unapplySeq(element: PsiElement): Option[Seq[DependencyDescriptor]] = element match {
      case ScMethodCall((_: ScReferenceExpression) & ElementText("Seq"), args) =>
        // we have traverse at home
        val dependencies = args.foldLeft(Option(Seq.newBuilder[DependencyDescriptor])) {
          case (Some(acc), SbtDependency(descriptor)) =>
            Some(acc += descriptor)
          case _ => None
        }.map(_.result())
        dependencies
      case _ =>
        None
    }
  }

  private object SbtLibraryDependencies {
    private val LibraryDependencies = "libraryDependencies"

    def unapplySeq(element: PsiElement): Option[Seq[DependencyDescriptor]] = element match {
      case ScInfixExpr(ElementText(LibraryDependencies), ElementText("+="), SbtDependency(descriptor)) =>
        Some(Seq(descriptor))
      case ScInfixExpr(ElementText(LibraryDependencies), ElementText("++=" | ":="), rhs) =>
        SeqOfSbtDependencies.unapplySeq(rhs)
      case _ => None
    }
  }
}

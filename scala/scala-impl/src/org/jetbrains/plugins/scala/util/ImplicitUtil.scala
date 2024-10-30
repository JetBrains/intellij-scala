package org.jetbrains.plugins.scala.util

import com.intellij.lang.{ASTNode, Language}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.impl.PsiElementBase
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.highlighter.usages.ScalaHighlightImplicitUsagesHandler.TargetKind._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScContextBound, ScSimpleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.annotation.tailrec

object ImplicitUtil {
  implicit class ImplicitTargetExt(private val targetImplicit: PsiElement) extends AnyVal {
    private def isTarget(srr: ScalaResolveResult): Boolean = srr.element match {
      case `targetImplicit`                                                => true
      case f: ScFunction if targetImplicit == f.syntheticNavigationElement => true
      case f: ScFunction if f.name == CommonNames.Apply            => srr.innerResolveResult.exists(isTarget)
      case _                                                               => false
    }

    private def matches(srr: ScalaResolveResult): Boolean =
      isTarget(srr) ||
        srr.implicitParameters.exists(matches) ||
        srr.implicitConversion.exists(matches)

    private def isImplicitConversionOrParameter(e: ScExpression): Boolean =
      e.implicitConversion().exists(matches) || isImplicitParameterOf(e)

    private def isImplicitParameterOf(e: ImplicitArgumentsOwner): Boolean =
      e.findImplicitArguments
        .getOrElse(Seq.empty)
        .exists(matches)

    def refOrImplicitRefIn(usage: PsiElement): Option[PsiReference] = usage match {
      case ref: ScReference if ref.bind().exists(isTarget)        => Option(ref)
      case e: ScExpression if isImplicitConversionOrParameter(e)  => Option(ImplicitReference(e, targetImplicit))
      case c: ScConstructorInvocation if isImplicitParameterOf(c) => Option(ImplicitReference(c, targetImplicit))
      case _                                                      => None
    }
  }

  object ImplicitSearchTarget {
    def unapply(e: PsiElement): Option[PsiNamedElement] = e match {
      case named: ScNamedElement       => namedKind.target(named)
      case ref: ScReference            => refKind.target(ref)
      case contextBoundElement(tp, te) => contextBoundKind.target(tp, te)
      case _                           => None
    }
  }

  @tailrec
  private def range(usage: PsiElement): TextRange = {
    val simpleRange = usage.getTextRange

    def startingFrom(elem: PsiElement): TextRange = {
      val start = elem.getTextRange.getStartOffset
      TextRange.create(start, simpleRange.getEndOffset)
    }

    def forTypeElem(typeElem: ScSimpleTypeElement) = {
      def newTd =
        Option(PsiTreeUtil.getParentOfType(typeElem, classOf[ScNewTemplateDefinition]))
          .filter(_.firstConstructorInvocation.flatMap(_.simpleTypeElement).contains(typeElem))

      def constructor =
        Option(PsiTreeUtil.getParentOfType(typeElem, classOf[ScConstructorInvocation]))
          .filter(_.simpleTypeElement.contains(typeElem))

      newTd
        .orElse(constructor)
        .getOrElse(typeElem)
        .getTextRange
    }

    usage match {
      case ScMethodCall(ScParenthesisedExpr(_), _)          => simpleRange
      case ScMethodCall(_: ScThisReference, _)              => simpleRange
      case MethodRepr(_: ScMethodCall, Some(base), None, _) => range(base)
      case MethodRepr(_, _, Some(ref), _)                   => startingFrom(ref.nameId)
      case simpleTypeElem: ScSimpleTypeElement              => forTypeElem(simpleTypeElem)
      case ref: ScReference                                 => startingFrom(ref.nameId)
      case _                                                => simpleRange
    }
  }

  private[this] def relativeRangeInElement(usage: PsiElement): TextRange =
    range(usage).shiftLeft(usage.getTextRange.getStartOffset)

  final case class ImplicitReference(e: PsiElement, targetImplicit: PsiElement)
      extends PsiReferenceBase[PsiElement](e, relativeRangeInElement(e), false) {
    override def resolve(): PsiElement      = targetImplicit
    override def getVariants: Array[AnyRef] = Array.empty
  }

  final case class UnresolvedImplicitFakePsiElement(project: Project, file: PsiFile, lineOffset: Int)
      extends PsiElementBase {
    override def getContainingFile: PsiFile        = file
    override def isValid: Boolean                  = true
    override def getProject: Project               = project
    override def getTextRange: TextRange           = TextRange.EMPTY_RANGE.shiftRight(lineOffset)
    override def getTextOffset: Int                = lineOffset
    override def getText: String                   = ""
    override def getChildren: Array[PsiElement]    = PsiElement.EMPTY_ARRAY
    override def getTextLength: Int                = 0
    override def getStartOffsetInParent: Int       = 0
    override def textToCharArray(): Array[Char]    = Array.emptyCharArray
    override def getParent: PsiElement             = null
    override def getLanguage: Language             = ScalaLanguage.INSTANCE
    override def getNode: ASTNode                  = null
    override def findElementAt(i: Int): PsiElement = null
  }

  private[this] object UnresolvedImplicitFakePsiElement {
    def apply(targetImplicit: PsiElement, file: PsiFile, lineOffset: Int): UnresolvedImplicitFakePsiElement = {
      val project = inReadAction(targetImplicit.getProject)
      new UnresolvedImplicitFakePsiElement(project, file, lineOffset)
    }
  }

  final case class UnresolvedImplicitReference(targetImplicit: PsiElement, file: PsiFile, lineOffset: Int)
      extends PsiReferenceBase[PsiElement](
        UnresolvedImplicitFakePsiElement(targetImplicit, file, lineOffset),
        TextRange.EMPTY_RANGE,
        false
      ) {
    override def resolve(): PsiElement      = targetImplicit
    override def getVariants: Array[AnyRef] = Array.empty
  }

  object contextBoundElement {

    def unapply(e: PsiElement): Option[(ScTypeParam, ScContextBound)] =
      (for {
        element <- NullSafe(e)
        if ScalaPsiUtil.stub(e).isNull && e.getLanguage.isKindOf(ScalaLanguage.INSTANCE)

        node <- NullSafe(e.getNode)
        if node.getElementType == ScalaTokenTypes.tCOLON

        parent = element.getParent
        if parent.isInstanceOf[ScTypeParam]
        typeParameter = parent.asInstanceOf[ScTypeParam]

        sibling = element.getNextSiblingNotWhitespaceComment
        if sibling.isInstanceOf[ScContextBound]
        typeElement = sibling.asInstanceOf[ScContextBound]
      } yield (typeParameter, typeElement)).toOption
  }
}

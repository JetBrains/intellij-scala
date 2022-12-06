package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement, ResolveState}
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScParameterOwner
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScReferenceImpl
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, CompletionProcessor, ResolveProcessor}
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult}
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing.{PARAM_TAG, TYPE_PARAM_TAG}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocReference, ScDocTag, ScDocTagValue}

final class ScDocTagValueImpl(node: ASTNode)
  extends ScReferenceImpl(node)
    with ScDocTagValue
    with ScDocReference {

  import ResolveTargets._

  override def nameId: PsiElement = this

  override def getName: String = getText

  override def qualifier: Option[ScalaPsiElement] = None

  override def getKinds(incomplete: Boolean, completion: Boolean): Set[Value] = Set(
    CLASS,
    VAL,
    VAR
  )

  override def getSameNameVariants: Array[ScalaResolveResult] = Array.empty

  override def multiResolveScala(incompleteCode: Boolean): Array[ScalaResolveResult] =
    doResolve(
      new ResolveProcessor(getKinds(incompleteCode), this, refName),
      accessibilityCheck = false
    )

  override def doResolve(processor: BaseProcessor, accessibilityCheck: Boolean): Array[ScalaResolveResult] = {
    if (!accessibilityCheck)
      processor.doNotCheckAccessibility()

    val isInsideCompletion = processor.is[CompletionProcessor]
    val parameters = getParametersScalaDocOwnerParametersOfMyTagKind(isInsideCompletion)
    parameters.foreach {
      processor.execute(_, ResolveState.initial)
    }
    processor.candidates
  }

  override def toString: String = "ScalaDocTagValue: " + getText

  override def bindToElement(element: PsiElement): PsiElement =
    element match {
      case _: ScParameter => this
      case _: ScTypeParam =>
        handleElementRename(element.getText)
        this
      case _ => throw new UnsupportedOperationException("Can't bind to this element")
    }

  override def getCanonicalText: String = if (getFirstChild == null) null else getFirstChild.getText

  override def isReferenceTo(@NotNull element: PsiElement): Boolean =
    resolve() == element

  override def handleElementRename(newElementName: String): PsiElement = {
    if (!isIdentifier(newElementName)) return this

    val doc = FileDocumentManager.getInstance().getDocument(getContainingFile.getVirtualFile)
    PsiDocumentManager.getInstance(getProject).doPostponedOperationsAndUnblockDocument(doc)
    val range: TextRange = getFirstChild.getTextRange
    doc.replaceString(range.getStartOffset, range.getEndOffset, newElementName)
    PsiDocumentManager.getInstance(getProject).commitAllDocuments()

    getElement
  }

  //NOTE: Looks like this is not used in completion, I am not sure when exactly this is used
  override def completionVariants(withImplicitConversions: Boolean): Array[ScalaResolveResult] = {
    val parameters = getParametersScalaDocOwnerParametersOfMyTagKind(excludeAlreadyMentioned = true)
    parameters.map(new ScalaResolveResult(_)).toArray
  }

  override def isSoft: Boolean = !isParamTag

  private def parentTagName: String =
    getParent.asOptionOf[ScDocTag].flatMap(_.getName.toOption).getOrElse("")

  private def isParamTag: Boolean = parentTagName match {
    case PARAM_TAG | TYPE_PARAM_TAG => true
    case _ => false
  }

  private def getParametersScalaDocOwnerParametersOfMyTagKind(excludeAlreadyMentioned: Boolean): Seq[ScNamedElement] = {
    val parentTagType = parentTagName
    val scalaDocComment = PsiTreeUtil.getParentOfType(this, classOf[ScDocComment])

    if (scalaDocComment == null || !isParamTag)
      return Nil

    val scalaDocOwner = scalaDocComment.getOwner
    val scalaDocOwnerParameters = (parentTagType, scalaDocOwner) match {
      case (PARAM_TAG, paramsOwner: ScParameterOwner) =>
        paramsOwner.parameters
      case (TYPE_PARAM_TAG, typeParamsOwner: ScTypeParametersOwner) =>
        typeParamsOwner.typeParameters
      case _ =>
        Nil
    }
    if (excludeAlreadyMentioned) {
      val existingParamTags: Array[PsiDocTag] = scalaDocComment.findTagsByName(parentTagName)
      val paramNamesAlreadyMentionedInTags: Array[String] = existingParamTags.flatMap(_.getValueElement.toOption).map(_.getText)
      scalaDocOwnerParameters.filterNot(p => paramNamesAlreadyMentionedInTags.contains(p.name))
    }
    else scalaDocOwnerParameters
  }
}
package org.jetbrains.plugins.scala
package annotator
package gutter

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.annotator.gutter.ScalaGoToDeclarationHandler._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType.IsTemplateDefinition
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScPackage
import org.jetbrains.plugins.scala.lang.psi.api.base.{Constructor, ScEnd, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScEnumerator, ScSelfInvocation}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelectors
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor

import scala.annotation.tailrec

class ScalaGoToDeclarationHandler extends GotoDeclarationHandler {

  override def getGotoDeclarationTargets(element: PsiElement, offset: Int, editor: Editor): Array[PsiElement] = {
    if (element == null) return null
    val containingFile = element.getContainingFile
    if (containingFile == null) return null
    val sourceElement = containingFile.findElementAt(offset)
    if (sourceElement == null) return null
    if (!sourceElement.getLanguage.isKindOf(ScalaLanguage.INSTANCE)) return null

    val maybeParent = sourceElement.parent
    maybeParent match {
      case Some(end: ScEnd) if end.tag == sourceElement =>
        return end.begin.map(_.tag).toArray
      case _ =>
    }

    sourceElement.getNode.getElementType match {
      case IsTemplateDefinition() =>
        val typeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition])
        typeDefinition.baseCompanion.map(_.nameId.getPrevSiblingNotWhitespace).toArray

      case ScalaTokenTypes.tASSIGN =>
        maybeParent
          .collect { case assign: ScAssignment => assign }
          .flatMap { assign => Option(assign.assignNavigationElement) }
          .map { Array(_) }
          .getOrElse { getGotoDeclarationTargetsForEnumerator(maybeParent) }

      case ScalaTokenTypes.kTHIS =>
        val maybeResult = maybeParent.flatMap {
          case self: ScSelfInvocation => self.bind
          case _ => None
        }

        maybeResult match {
          case Some(result) => Array(result)
          case _ => null
        }

      case ScalaTokenTypes.kIF =>
        getGotoDeclarationTargetsForEnumerator(maybeParent)

      case ScalaTokenTypes.tCHOOSE =>
        getGotoDeclarationTargetsForEnumerator(maybeParent)

      case ScalaTokenTypes.tIDENTIFIER =>
        val reference = containingFile.findReferenceAt(sourceElement.getTextRange.getStartOffset)
        if (reference == null)
          return null

        getGotoDeclarationTargetsForElement(reference, maybeParent)

      case _ => null
    }
  }

  override def getActionText(context: DataContext): String = null
}

object ScalaGoToDeclarationHandler {
  private def getGotoDeclarationTargetsForEnumerator(maybeParent: Option[PsiElement]): Array[PsiElement] = {
    maybeParent
      .collect { case enumerator: ScEnumerator => enumerator }
      .flatMap { _.desugared }
      .flatMap { _.callExpr }
      .map { expr => getGotoDeclarationTargetsForElement(expr, Some(expr))}
      .orNull
  }

  private def getGotoDeclarationTargetsForElement(reference: PsiReference,
                                                  maybeParent: Option[PsiElement]): Array[PsiElement] = {

    val targets = reference match {
      case DynamicResolveProcessor.DynamicReference(results) =>
        results.toSet[ResolveResult]
          .map(_.getElement)
          .filterNot(_ == null)
      case referenceElement: ScReference =>
        referenceElement.multiResolveScala(incomplete = false)
          .toSet[ScalaResolveResult]
          .flatMap {
            case ScalaResolveResult(pkg: ScPackage, _) => packageCase(pkg, maybeParent)
            case result => regularCase(result)
          }
      case ResolvesTo(resolved) => Set(resolved)
      case _ => return null
    }

    targets.flatMap { element =>
      val syntheticTargets = syntheticTarget(element)
      if (syntheticTargets.isEmpty) Seq(element)
      else                          syntheticTargets
    }.toArray
  }

  private def regularCase(result: ScalaResolveResult): Seq[PsiElement] = {
    val actualElement = result.getActualElement
    result.element match {
      case function: ScFunction if function.isSynthetic =>
        Seq(function.syntheticCaseClass match {
          case null => actualElement
          case clazz => clazz
        })
      case constr@Constructor.ofClass(`actualElement`) => Seq(constr)
      case element => Seq(actualElement, element) ++ result.innerResolveResult.map(_.getElement)
    }
  }

  private class IsReferenced(elements: Seq[PsiNamedElement]) {

    val (fromPackageObject: Boolean, fromPackage: Boolean) = {
      val (left, right) = elements.partition(isInPackageObject)
      (left.nonEmpty, right.nonEmpty)
    }

    @tailrec
    private def isInPackageObject(element: PsiElement): Boolean = element match {
      case member: ScMember if member.isSynthetic => isInPackageObject(member.syntheticNavigationElement)
      case _ => element.parentOfType(classOf[ScObject]).exists(_.isPackageObject)
    }
  }

  private def packageCase(pkg: ScPackage, maybeParent: Option[PsiElement]): Iterable[PsiElement] = {
    import ScalaTokenTypes.{tDOT, tUNDER}
    val maybePackageObject = pkg.findPackageObject(pkg.getResolveScope)

    val maybeSegment = for {
      _ <- maybePackageObject
      parent <- maybeParent
      dot <- Option(parent.getNextSiblingNotWhitespaceComment)
      if dot.getNode.getElementType == tDOT
      segment <- Option(dot.getNextSiblingNotWhitespaceComment)
    } yield segment

    val references = maybeSegment.toSet[PsiElement].flatMap {
      case selectors: ScImportSelectors if !selectors.hasWildcard => selectors.selectors.flatMap(_.reference)
      case _: ScImportSelectors => Seq.empty
      case underscore if underscore.getNode.getElementType == tUNDER => Seq.empty
      case ident => ident.parentOfType(classOf[ScReference]).toSeq
    }

    val set = references.flatMap(isReferencedFrom)
    val packageRequired = isRequired(set)(_.fromPackage)
    val packageObjectRequired = isRequired(set)(_.fromPackageObject)

    (if (packageRequired) Some(pkg) else None) ++
      (if (packageObjectRequired) maybePackageObject else None)
  }

  private[this] def isReferencedFrom(reference: ScReference): Option[IsReferenced] =
    reference.multiResolveScala(false) match {
      case Array() => None
      case results => Some(new IsReferenced(results.map(_.element).toIndexedSeq))
    }

  private[this] def isRequired(set: Set[IsReferenced])
                              (predicate: IsReferenced => Boolean) =
    set.isEmpty || set.exists(predicate)

  import ScalaPsiUtil.{getCompanionModule, parameterForSyntheticParameter}

  private def syntheticTarget(element: PsiElement): Seq[PsiElement] =
    element match {
      case ScEnum.Original(enum)                            => Seq(enum)
      case ScEnumCase.Original(enumCase)                    => Seq(enumCase)
      case ScGivenDefinition.DesugaredTypeDefinition(gvn)   => Seq(gvn)
      case function: ScFunction                             => Option(function.syntheticNavigationElement).toSeq
      case scObject: ScObject if scObject.isSyntheticObject =>
        val companionClass = getCompanionModule(scObject)
        companionClass.collect {
          case ScEnum.Original(enum) => enum
        }.orElse(companionClass).toSeq
      case definition: ScTypeDefinition if definition.isSynthetic => Option(definition.syntheticContainingClass).toSeq
      case parameter: ScParameter                                 => parameterForSyntheticParameter(parameter).toSeq
      case _                                                      => Seq.empty
    }
}
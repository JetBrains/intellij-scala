package org.jetbrains.plugins.scala
package annotator
package gutter

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScPackage
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignStmt, ScSelfInvocation}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelectors
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor

/**
  * User: Alexander Podkhalyuzin
  * Date: 22.11.2008
  */

class ScalaGoToDeclarationHandler extends GotoDeclarationHandler {

  def getGotoDeclarationTargets(_sourceElement: PsiElement, offset: Int, editor: Editor): Array[PsiElement] = {
    if (_sourceElement == null) return null
    val containingFile: PsiFile = _sourceElement.getContainingFile
    if (containingFile == null) return null
    val sourceElement = containingFile.findElementAt(offset)
    if (sourceElement == null) return null
    if (!sourceElement.getLanguage.isKindOf(ScalaLanguage.INSTANCE)) return null

    sourceElement.getNode.getElementType match {
      case ScalaTokenTypes.tASSIGN =>
        val maybeResult = sourceElement.getParent match {
          case assign: ScAssignStmt => Option(assign.assignNavigationElement)
          case _ => None
        }

        maybeResult match {
          case Some(result) => Array(result)
          case _ => null
        }
      case ScalaTokenTypes.kTHIS =>
        val maybeResult = sourceElement.getParent match {
          case self: ScSelfInvocation => self.bind
          case _ => None
        }

        maybeResult match {
          case Some(result) => Array(result)
          case _ => null
        }
      case ScalaTokenTypes.tIDENTIFIER =>
        val reference = sourceElement.getContainingFile
          .findReferenceAt(sourceElement.getTextRange.getStartOffset)

        import ScalaGoToDeclarationHandler._
        val targets = reference match {
          case DynamicResolveProcessor.DynamicReference(results) =>
            results.toSet[ResolveResult]
              .map(_.getElement)
              .filterNot(_ == null)
          case resRef: ScReferenceElement =>
            resRef.multiResolveScala(incomplete = false)
              .toSet[ScalaResolveResult]
              .flatMap(handleScalaResolveResult(_, sourceElement))
          case ResolvesTo(element) => Set(element)
          case _ => return null
        }

        targets.map { element =>
          syntheticTarget(element).getOrElse(element)
        }.toArray
      case _ => null
    }
  }

  def getActionText(context: DataContext): String = null
}

object ScalaGoToDeclarationHandler {

  /**
    * Extra targets:
    *
    * actualElement              type alias used to access a constructor.
    * See also [[org.jetbrains.plugins.scala.findUsages.TypeAliasUsagesSearcher]]
    * innerResolveResult#element apply method
    */
  private def handleScalaResolveResult(result: ScalaResolveResult, sourceElement: PsiElement): Set[PsiElement] = {
    val element = result.element
    val actualElement = result.getActualElement

    val all = Set[PsiElement](actualElement, element) ++
      result.innerResolveResult.map(_.getElement)

    element match {
      case f: ScFunction if f.isSynthetic =>
        Set(f.syntheticCaseClass.getOrElse(actualElement))
      case c: PsiMethod if c.isConstructor =>
        c.containingClass match {
          case `actualElement` => Set(element)
          case _ => all
        }
      case pkg: ScPackage => resolvePackageTargets(sourceElement, pkg)
      case _ => all
    }
  }

  private[this] def resolvePackageTargets(pkgSourceElem: PsiElement, pkg: ScPackage): Set[PsiElement] = {
    def isInPackageObject: PsiElement => Boolean = {
      case member: ScMember if member.isSynthetic => member.getSyntheticNavigationElement.exists(isInPackageObject)
      case e => e.parentOfType(classOf[ScObject]).exists(_.isPackageObject)
    }

    def foldAmbiguously(xs: Traversable[(Boolean, Boolean)]): (Boolean, Boolean) =
      if (xs.isEmpty) (true, true)
      else
        xs.foldLeft((false, false)) {
          case ((x1, y1), (x2, y2)) => (x1 || x2, y1 || y2)
        }

    def hasReferenceToElementFromPackageObject(refs: Traversable[ScReferenceElement]): (Boolean, Boolean) =
      foldAmbiguously(refs.map { r =>
        val resolves = r.multiResolveScala(false)

        foldAmbiguously(resolves.map { e =>
          val inPackageObject = isInPackageObject(e.element)
          inPackageObject -> !inPackageObject
        })
      })

    val pkgObjectTarget = pkg.findPackageObject(pkg.getResolveScope)

    val nextSegment = for {
      _ <- pkgObjectTarget
      parent <- pkgSourceElem.parent
      dot <- Option(parent.getNextSiblingNotWhitespaceComment)
      if dot.getNode.getElementType == ScalaTokenTypes.tDOT
      next <- Option(dot.getNextSiblingNotWhitespaceComment)
    } yield next

    val (hasReferenceFromPackageObject, hasReferenceFromPackage) = nextSegment.collect {
      case selectors: ScImportSelectors =>
        val hasWildcard = selectors.hasWildcard
        if (hasWildcard) (true, true)
        else {
          val refs = selectors.selectors.flatMap(_.reference)
          hasReferenceToElementFromPackageObject(refs)
        }
      case under if under.getNode.getElementType == ScalaTokenTypes.tUNDER =>
        (true, true)
      case ident =>
        hasReferenceToElementFromPackageObject(ident.parentOfType(classOf[ScReferenceElement]))
    }.getOrElse((true, true))

    val pkgTarget = if (hasReferenceFromPackage) Set(pkg) else Set.empty
    pkgTarget ++ (if (hasReferenceFromPackageObject) pkgObjectTarget else None)
  }

  import ScalaPsiUtil.{getCompanionModule, parameterForSyntheticParameter}

  private def syntheticTarget(element: PsiElement): Option[PsiElement] = element match {
    case function: ScFunction => function.getSyntheticNavigationElement
    case definition: ScTypeDefinition if definition.isSynthetic => definition.syntheticContainingClass
    case scObject: ScObject if scObject.isSyntheticObject => getCompanionModule(scObject)
    case parameter: ScParameter => parameterForSyntheticParameter(parameter)
    case _ => None
  }
}
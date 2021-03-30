package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package packaging

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.TokenSets.TYPE_DEFINITIONS
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{FileDeclarationsHolder, ScPackageLike, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.packaging.ScPackagingImpl.LeftBraceOrColon
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPackagingStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubElementType

/**
  * @author Alexander Podkhalyuzin, Pavel Fatin
  *         Date: 20.02.2008
  */
final class ScPackagingImpl private[psi](stub: ScPackagingStub,
                                         nodeType: ScStubElementType[ScPackagingStub, ScPackaging],
                                         node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node)
    with ScPackaging
    with ScImportsHolder // todo: to be removed
    with ScDeclarationSequenceHolder {
  import ScPackageLike._

  override def toString = "ScPackaging"

  override def reference: Option[ScStableCodeReference] =
    Option(getFirstChild).flatMap { node =>
      Option(node.getNextSibling)
    }.flatMap { node =>
      Option(node.getNextSibling)
    }.collect {
      case reference: ScStableCodeReference => reference
    }.orElse {
      findChild[ScStableCodeReference]
    }

  override def packagings: Seq[ScPackaging] =
    getStubOrPsiChildren(ScalaElementType.PACKAGING, JavaArrayFactoryUtil.ScPackagingFactory).toSeq

  override def isExplicit: Boolean = byStubOrPsi(_.isExplicit)(findExplicitMarker.isDefined)
  override def findExplicitMarker: Option[PsiElement] =
    Option(findChildByFilter(LeftBraceOrColon))

  override def packageName: String = byStubOrPsi(_.packageName)(reference.fold("")(_.qualName))

  override def parentPackageName: String = byStubOrPsi(_.parentPackageName)(ScPackagingImpl.parentPackageName(this))

  override def fullPackageName: String = ScPackagingImpl.fullPackageName(parentPackageName, packageName)

  override def declaredElements: Seq[ScPackageImpl] = {
    val name = packageName
    val topRefName = name.indexOf(".") match {
      case -1 => name
      case index => name.substring(0, index)
    }

    val top = ScPackagingImpl.fullPackageName(parentPackageName, topRefName)
    findPackage(top).toSeq
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    if (DumbService.getInstance(getProject).isDumb) return true

    val isTreeWalkUp = lastParent != null && lastParent.getContext == this

    if (isTreeWalkUp && FileDeclarationsHolder.isProcessLocalClasses(lastParent) &&
      !super[ScDeclarationSequenceHolder].processDeclarations(processor, state, lastParent, place)) return false

    //If stub is not null, then we are not trying to resolve packaging reference.
    if (getStub != null || !reference.contains(lastParent)) {
      ProgressManager.checkCanceled()

      findPackage(fullPackageName) match {
        case Some(p) if !p.processDeclarations(processor, state, lastParent, place) => return false
        case _                                                                      =>
      }

      if (!findPackageObject(place.resolveScope)
            .forall(processPackageObject(_)(processor, state, lastParent, place)))
        return false

      if (this.isInScala3Module) {
        if (!processTopLevelDeclarations(processor, state, place)) return false
      }
    }

    if (isTreeWalkUp) {
      if (!super[ScImportsHolder].processDeclarations(processor, state, lastParent, place))
        return false
    }

    true
  }

  override def findPackageObject(scope: GlobalSearchScope): Option[ScObject] =
    ScalaShortNamesCacheManager.getInstance(getProject)
      .findPackageObjectByName(fullPackageName, scope)

  override def fqn: String = fullPackageName

  override def bodyText: String = {
    val text = getText
    val endOffset = text.length

    findExplicitMarker match {
      case Some(brace) =>
        val startOffset = brace.getTextRange.getEndOffset - getTextRange.getStartOffset

        val length = if (text(text.length - 1) == '}') 1 else 0
        text.substring(startOffset, endOffset - length)
      case _ =>
        var ref = findChildByType[PsiElement](ScalaElementType.REFERENCE)
        if (ref == null) ref = findChildByType[PsiElement](ScalaTokenTypes.kPACKAGE)
        if (ref == null) return text

        val startOffset = ref.getTextRange.getEndOffset + 1 - getTextRange.getStartOffset
        if (startOffset >= endOffset) "" else text.substring(startOffset, endOffset)
    }
  }

  override protected def childBeforeFirstImport: Option[PsiElement] =
    findExplicitMarker.orElse(reference)

  override def parentScalaPackage: Option[ScPackageLike] = {
    Option(PsiTreeUtil.getContextOfType(this, true, classOf[ScPackageLike])).orElse {
      ScalaPsiUtil.parentPackage(fullPackageName, getProject)
    }
  }

  override def immediateTypeDefinitions: Seq[ScTypeDefinition] =
    getStubOrPsiChildren(TYPE_DEFINITIONS, JavaArrayFactoryUtil.ScTypeDefinitionFactory).toSeq

  private def findPackage(name: String) =
    Option(JavaPsiFacade.getInstance(getProject).findPackage(name))
      .map(ScPackageImpl(_))
}

object ScPackagingImpl {

  private val LeftBraceOrColon = TokenSet.create(ScalaTokenTypes.tLBRACE, ScalaTokenTypes.tCOLON)

  private def fullPackageName(parentPackageName: String, packageName: String): String = {
    val infix = parentPackageName match {
      case "" => ""
      case _ => "."
    }
    s"$parentPackageName$infix$packageName"
  }

  private def parentPackageName(element: PsiElement): String = element.getParent match {
    case packaging: ScPackaging =>
      fullPackageName(parentPackageName(packaging), packaging.packageName)
    case _: ScalaFile |
         null => ""
    case parent => parentPackageName(parent)
  }
}


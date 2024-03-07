package org.jetbrains.plugins.scala.lang.psi.impl.toplevel
package packaging

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, ToNullSafe}
import org.jetbrains.plugins.scala.lang.TokenSets.{MEMBERS, TYPE_DEFINITIONS}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{FileDeclarationsHolder, ScBegin, ScPackageLike, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScPackageImpl, ScalaStubBasedElementImpl}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPackagingStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubElementType
import org.jetbrains.plugins.scala.lang.psi.{ScDeclarationSequenceHolder, ScExportsHolder, ScImportsHolder, ScalaPsiUtil}

final class ScPackagingImpl private[psi](stub: ScPackagingStub,
                                         nodeType: ScStubElementType[ScPackagingStub, ScPackaging],
                                         node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node)
    with ScPackaging
    with ScImportsHolder // todo: to be removed
    with ScExportsHolder
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

  override def getEnclosingStartElement: Option[PsiElement] =
    this.getNode
      .nullSafe
      .map(_.findChildByType(ScalaTokenTypes.LBRACE_OR_COLON_TOKEN_SET))
      .map(_.getPsi)
      .toOption

  override def isExplicit: Boolean = byStubOrPsi(_.isExplicit)(findExplicitMarker.isDefined)
  override def findExplicitMarker: Option[PsiElement] = getEnclosingStartElement

  override def packageName: String = byStubOrPsi(_.packageName)(reference.fold("")(_.qualName))

  override def parentPackageName: String = byStubOrPsi(_.parentPackageName)(ScPackagingImpl.getParentPackageName(this))

  override def fullPackageName: String = ScPackagingImpl.getFullPackageName(parentPackageName, packageName)

  override def declaredElements: Seq[ScPackageImpl] = {
    val name = packageName
    val topRefName = name.indexOf(".") match {
      case -1 => name
      case index => name.substring(0, index)
    }

    val top = ScPackagingImpl.getFullPackageName(parentPackageName, topRefName)
    findPackage(top).toSeq
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    if (DumbService.getInstance(getProject).isDumb) return true

    val isTreeWalkUp = lastParent != null && lastParent.getContext == this

    if (isTreeWalkUp && FileDeclarationsHolder.isProcessLocalClasses(lastParent) &&
      !super[ScDeclarationSequenceHolder].processDeclarations(processor, state, lastParent, place))
      return false

    //If stub is not null, then we are not trying to resolve packaging reference.
    if (getStub != null || !reference.contains(lastParent)) {
      ProgressManager.checkCanceled()

      val foundPackage = findPackage(fullPackageName)
      foundPackage match {
        case Some(p) if !p.processDeclarations(processor, state, lastParent, place) =>
          return false
        case _                                                                      =>
      }

      val foundPackageObject = findPackageObject(place.resolveScope)
      if (!foundPackageObject.forall(processPackageObject(_)(processor, state, lastParent, place)))
        return false

      if (this.isInScala3Module) {
        if (!processTopLevelDeclarations(processor, state, lastParent, place))
          return false
      }
    }

    if (isTreeWalkUp) {
      if (!processDeclarationsFromImports(processor, state, lastParent, place))
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

  override def immediateMembers: Seq[ScMember] =
    getStubOrPsiChildren(MEMBERS, JavaArrayFactoryUtil.ScMemberFactory).toSeq

  private def findPackage(name: String): Option[ScPackageImpl] = {
    val found = JavaPsiFacade.getInstance(getProject).findPackage(name)
    Option(found).map(ScPackageImpl(_))
  }

  override protected def keywordTokenType: IElementType = ScalaTokenTypes.kPACKAGE

  override def tag: PsiElement = reference.getOrElse(keyword)
}

object ScPackagingImpl {

  private def getFullPackageName(parentPackageName: String, packageName: String): String =
    if (parentPackageName.isEmpty)
      packageName
    else
      s"$parentPackageName.$packageName"

  private def getParentPackageName(element: PsiElement): String = element.getParent match {
    case parentPackage: ScPackaging =>
      val parentPackageName = getParentPackageName(parentPackage)
      getFullPackageName(parentPackageName, parentPackage.packageName)
    case _: ScalaFile | null =>
      ""
    case parent =>
      getParentPackageName(parent)
  }
}

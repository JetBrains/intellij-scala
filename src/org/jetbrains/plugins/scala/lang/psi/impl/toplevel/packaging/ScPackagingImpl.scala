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
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil.ScTypeDefinitionFactory
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.lang.TokenSets.TYPE_DEFINITIONS
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes.PACKAGING
import org.jetbrains.plugins.scala.lang.psi.api.ScPackageLike
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPackagingStub
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor

/**
  * @author Alexander Podkhalyuzin, Pavel Fatin
  *         Date: 20.02.2008
  */
class ScPackagingImpl private(stub: ScPackagingStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, PACKAGING, node) with ScPackaging with ScImportsHolder with ScDeclarationSequenceHolder {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScPackagingStub) = this(stub, null)

  override def toString = "ScPackaging"

  def reference: Option[ScStableCodeReferenceElement] =
    Option(getFirstChild).flatMap { node =>
      Option(node.getNextSibling)
    }.flatMap { node =>
      Option(node.getNextSibling)
    }.collect {
      case reference: ScStableCodeReferenceElement => reference
    }.orElse {
      findChild(classOf[ScStableCodeReferenceElement])
    }

  def isExplicit: Boolean = byStubOrPsi(_.isExplicit)(findChildByType(ScalaTokenTypes.tLBRACE) != null)

  def packageName: String = byStubOrPsi(_.packageName)(reference.map(_.qualName).getOrElse(""))

  def parentPackageName: String = byStubOrPsi(_.parentPackageName)(parentPackageName(this))

  private def parentPackageName(e: PsiElement): String = e.getParent match {
    case p: ScPackaging => ScPackaging.fullPackageName(parentPackageName(p), p.packageName)
    case _: ScalaFileImpl | null => ""
    case parent => parentPackageName(parent)
  }

  def typeDefs: Seq[ScTypeDefinition] = getStubOrPsiChildren(TYPE_DEFINITIONS, ScTypeDefinitionFactory)

  def declaredElements: Seq[ScPackageImpl] = {
    val topRefName = packageName.indexOf(".") match {
      case -1 => packageName
      case index => packageName.substring(0, index)
    }

    val top = ScPackaging.fullPackageName(parentPackageName, topRefName)
    Option(JavaPsiFacade.getInstance(getProject).findPackage(top)).map {
      ScPackageImpl(_)
    }.toSeq
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    if (DumbService.getInstance(getProject).isDumb) return true

    //If stub is not null, then we are not trying to resolve packaging reference.
    if (getStub != null || !reference.contains(lastParent)) {
      ProgressManager.checkCanceled()

      val scPackageImpl = Option(JavaPsiFacade.getInstance(getProject)
        .findPackage(fullPackageName))
        .map(ScPackageImpl(_))

      scPackageImpl match {
        case Some(p) if !p.processDeclarations(processor, state, lastParent, place) => return false
        case _ =>
      }

      findPackageObject(place.getResolveScope).foreach { definition =>
        var newState = state
        definition.getType().foreach { tp =>
          newState = state.put(BaseProcessor.FROM_TYPE_KEY, tp)
        }
        if (!definition.processDeclarations(processor, newState, lastParent, place)) return false
      }
    }

    if (lastParent != null && lastParent.getContext == this) {
      if (!super[ScImportsHolder].processDeclarations(processor,
        state, lastParent, place)) return false

      if (ScalaFileImpl.isProcessLocalClasses(lastParent) &&
        !super[ScDeclarationSequenceHolder].processDeclarations(processor, state, lastParent, place)) return false
    }

    true
  }

  def findPackageObject(scope: GlobalSearchScope): Option[ScTypeDefinition] = {
    Option(ScalaShortNamesCacheManager.getInstance(getProject).getPackageObjectByName(fullPackageName, scope))
  }

  def getBodyText: String = {
    if (isExplicit) {
      val startOffset = findChildByType[PsiElement](ScalaTokenTypes.tLBRACE).getTextRange.getEndOffset - getTextRange.getStartOffset
      val text = getText
      val endOffset = if (text.apply(text.length - 1) == '}') {
        text.length - 1
      } else text.length
      text.substring(startOffset, endOffset)
    } else {
      val text = getText
      val endOffset = text.length
      var ref = findChildByType[PsiElement](ScalaElementTypes.REFERENCE)
      if (ref == null) ref = findChildByType[PsiElement](ScalaTokenTypes.kPACKAGE)
      if (ref == null) return getText
      val startOffset = ref.getTextRange.getEndOffset + 1 -
        getTextRange.getStartOffset
      if (startOffset >= endOffset) "" else text.substring(startOffset, endOffset)
    }
  }

  override protected def childBeforeFirstImport: Option[PsiElement] = {
    if (isExplicit) Option(findChildByType[PsiElement](ScalaTokenTypes.tLBRACE))
    else reference
  }

  override def parentScalaPackage: Option[ScPackageLike] = {
    Option(PsiTreeUtil.getContextOfType(this, true, classOf[ScPackageLike])).orElse {
      ScalaPsiUtil.parentPackage(fullPackageName, getProject)
    }
  }

  override def immediateTypeDefinitions: Seq[ScTypeDefinition] =
    byStubOrPsi(_.getChildrenByType(TYPE_DEFINITIONS, ScTypeDefinitionFactory)) {
      findChildrenByClassScala(classOf[ScTypeDefinition])
    }

}

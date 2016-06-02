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
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPackageContainerStub
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.collection.mutable.ArrayBuffer

/**
 * @author Alexander Podkhalyuzin, Pavel Fatin
 * Date: 20.02.2008
 */

class ScPackagingImpl private (stub: StubElement[ScPackageContainer], nodeType: IElementType, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node) with ScPackaging with ScImportsHolder with ScDeclarationSequenceHolder {
  def this(node: ASTNode) = {this(null, null, node)}

  def this(stub: ScPackageContainerStub) = {this(stub, ScalaElementTypes.PACKAGING, null)}

  def fullPackageName: String = (if (prefix.length == 0) "" else prefix + ".") + getPackageName

  override def toString = "ScPackaging"

  def reference: Option[ScStableCodeReferenceElement] = {
    val firstChild = getFirstChild
    if (firstChild == null) return None
    var next = firstChild.getNextSibling
    if (next == null) return None
    next = next.getNextSibling
    if (next == null) return None
    next match {
      case ref: ScStableCodeReferenceElement => Some(ref)
      case _ => findChild(classOf[ScStableCodeReferenceElement])
    }
  }

  def getPackageName = ownNamePart

  def isExplicit: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScPackageContainerStub].isExplicit
    }
    findChildByType[PsiElement](ScalaTokenTypes.tLBRACE) != null
  }

  def ownNamePart: String = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScPackageContainerStub].ownNamePart
    }
    reference match {case Some(r) => r.qualName case None => ""}
  }

  def prefix: String = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScPackageContainerStub].prefix
    }
    def parentPackageName(e: PsiElement): String = e.getParent match {
      case p: ScPackaging =>
        val _packName = parentPackageName(p)
        if (_packName.length > 0) _packName + "." + p.getPackageName else p.getPackageName
      case f: ScalaFileImpl => "" //f.getPackageName
      case null => ""
      case parent => parentPackageName(parent)
    }
    parentPackageName(this)
  }

  def typeDefs = {
    val stub = getStub
    if (stub != null) {
      stub.getChildrenByType(getProject.tokenSets.templateDefinitionSet, JavaArrayFactoryUtil.ScTypeDefinitionFactory)
    } else {
      val buffer = new ArrayBuffer[ScTypeDefinition]
      var curr = getFirstChild
      while (curr != null) {
        curr match {
          case definition: ScTypeDefinition => buffer += definition
          case _ =>
        }
        curr = curr.getNextSibling
      }
      buffer
      //findChildrenByClass[ScTypeDefinition](classOf[ScTypeDefinition])
    }
  }

  def declaredElements = {
    val _prefix = prefix
    val packageName = getPackageName
    val topRefName = if (packageName.indexOf(".") != -1) {
      packageName.substring(0, packageName.indexOf("."))
    } else packageName
    val top = if (_prefix.length > 0) _prefix + "." + topRefName else topRefName
    val p = ScPackageImpl(JavaPsiFacade.getInstance(getProject).findPackage(top))
    if (p == null) Seq.empty else Seq(p)
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    if (DumbService.getInstance(getProject).isDumb) return true

    //If stub is not null, then we are not trying to resolve packaging reference.
    if (getStub != null || !reference.contains(lastParent)) {
      val pName = (if (prefix.length == 0) "" else prefix + ".") + getPackageName
      ProgressManager.checkCanceled()
      val p = ScPackageImpl(JavaPsiFacade.getInstance(getProject).findPackage(pName))
      if (p != null && !p.processDeclarations(processor, state, lastParent, place)) {
        return false
      }

      findPackageObject(place.getResolveScope) match {
        case Some(po) =>
          var newState = state
          po.getType(TypingContext.empty).foreach {
            case tp: ScType => newState = state.put(BaseProcessor.FROM_TYPE_KEY, tp)
          }
          if (!po.processDeclarations(processor, newState, lastParent, place)) return false
        case _ =>
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
    Option(ScalaShortNamesCacheManager.getInstance(getProject).getPackageObjectByName(getPackageName, scope))
  }


  def getBodyText: String = {
    if (isExplicit) {
      val startOffset = findChildByType[PsiElement](ScalaTokenTypes.tLBRACE).getTextRange.getEndOffset - getTextRange.getStartOffset
      val text = getText
      val endOffset = if (text.apply(text.length - 1) == '}') {text.length - 1} else text.length
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
}
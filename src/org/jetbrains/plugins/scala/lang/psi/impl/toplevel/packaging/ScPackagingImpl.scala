package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package packaging

import api.base.ScStableCodeReferenceElement
import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import lexer.ScalaTokenTypes
import parser.ScalaElementTypes
import api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPackageContainerStub
import com.intellij.openapi.progress.ProgressManager
import java.lang.String
import api.toplevel.typedef.ScTypeDefinition
import search.GlobalSearchScope
import tree.TokenSet
import collection.mutable.ArrayBuffer
import com.intellij.openapi.project.DumbService
import caches.ScalaShortNamesCacheManager
import types.result.TypingContext
import types.ScType
import lang.resolve.processor.BaseProcessor

/**
 * @author Alexander Podkhalyuzin, Pavel Fatin
 * Date: 20.02.2008
 */

class ScPackagingImpl extends ScalaStubBasedElementImpl[ScPackageContainer] with ScPackaging with ScImportsHolder with ScDeclarationSequenceHolder {
  def this(node: ASTNode) = {this (); setNode(node)}

  def this(stub: ScPackageContainerStub) = {this (); setStub(stub); setNode(null)}

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

  def isExplicit = findChildByType(ScalaTokenTypes.tLBRACE) != null

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
      case p: ScPackaging => {
        val _packName = parentPackageName(p)
        if (_packName.length > 0) _packName + "." + p.getPackageName else p.getPackageName
      }
      case f: ScalaFileImpl => "" //f.getPackageName
      case null => ""
      case parent => parentPackageName(parent)
    }
    parentPackageName(this)
  }

  def typeDefs = {
    val stub = getStub
    if (stub != null) {
      stub.getChildrenByType(TokenSet.create(
        ScalaElementTypes.OBJECT_DEF,
        ScalaElementTypes.CLASS_DEF,
        ScalaElementTypes.TRAIT_DEF
        ), JavaArrayFactoryUtil.ScTypeDefinitionFactory)
    } else {
      val buffer = new ArrayBuffer[ScTypeDefinition]
      var curr = getFirstChild
      while (curr != null) {
        if (curr.isInstanceOf[ScTypeDefinition]) buffer += curr.asInstanceOf[ScTypeDefinition]
        curr = curr.getNextSibling
      }
      buffer.toSeq
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

    if (lastParent != null && lastParent.getContext == this) {
      if (!super[ScImportsHolder].processDeclarations(processor,
        state, lastParent, place)) return false
    }

    true
  }
  
  def findPackageObject(scope: GlobalSearchScope): Option[ScTypeDefinition] = {
    Option(ScalaShortNamesCacheManager.getInstance(getProject).getPackageObjectByName(getPackageName, scope))
  }


  def getBodyText: String = {
    if (isExplicit) {
      val startOffset = findChildByType(ScalaTokenTypes.tLBRACE).getTextRange.getEndOffset - getTextRange.getStartOffset
      val text = getText
      val endOffset = if (text.apply(text.length - 1) == '}') {text.length - 1} else text.length
      text.substring(startOffset, endOffset)
    } else {
      val text = getText
      val endOffset = text.length
      var ref = findChildByType(ScalaElementTypes.REFERENCE)
      if (ref == null) ref = findChildByType(ScalaTokenTypes.kPACKAGE)
      if (ref == null) return getText
      val startOffset = ref.getTextRange.getEndOffset + 1 -
              getTextRange.getStartOffset
      text.substring(startOffset, endOffset)
    }
  }
}
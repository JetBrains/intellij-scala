package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.types.ScSubstitutor
import com.intellij.psi.PsiPolyVariantReference
import org.jetbrains.plugins.scala.lang.psi.types._
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.resolve._
import statements.ScTypeAlias

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScReferenceElement extends ScalaPsiElement with PsiPolyVariantReference {

  def bind(): Option[ScalaResolveResult] = {
    val results = multiResolve(false)
    results.length match {
      case 1 => Some(results(0).asInstanceOf[ScalaResolveResult])
      case _ => None
    }
  }

  def resolve(): PsiElement = bind match {
    case None => null
    case Some(res) => res.element
  }

  override def getReference = this

  def nameId: PsiElement

  def refName: String = nameId.getText

  def getElement = this

  def getRangeInElement: TextRange =
    if (nameId != null)
      new TextRange(nameId.getTextRange.getStartOffset - getTextRange.getStartOffset, getTextLength)
    else getTextRange

  def getCanonicalText: String = null

  def isSoft(): Boolean = false

  def handleElementRename(newElementName: String): PsiElement = {
    return this;
    //todo
  }

  def isReferenceTo(element: PsiElement): Boolean = resolve() == element

  import ResolveTargets._
  def processType(t: ScType, processor: BaseProcessor): Boolean = t match {
    case ScDesignatorType(e) => e match {
      case ta : ScTypeAlias => processType(ta.upperBound, processor)
      case _ => e.processDeclarations(processor, ResolveState.initial, null, this)
    }
    case p: ScParameterizedType => p.designated match {
        case ta : ScTypeAlias => processType(p.substitutor.subst(ta.upperBound), processor)
        case des => des.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, p.substitutor), null, this)
      }
    case ScCompoundType(comp, decls, types) => {
      if (processor.kinds.contains(VAR) || processor.kinds.contains(VAL) || processor.kinds.contains(METHOD)) {
        for (decl <- decls) {
          for (declared <- decl.declaredElements) {
            if (!processor.execute(decl, ResolveState.initial)) return false
          }
        }
      }

      if (processor.kinds.contains(TYPE)) {
        for (t <- types) {
          if (!processor.execute(t, ResolveState.initial)) return false
        }
      }

      for (c <- comp) {
        if (!processType(c, processor)) return false
      }
      true
    }
    case _ => true //todo
  }
}
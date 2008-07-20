package org.jetbrains.plugins.scala.lang.psi.api.base

import impl.toplevel.synthetic.SyntheticClasses
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
    else new TextRange(0, getTextLength)

  def getCanonicalText: String = null

  def isSoft(): Boolean = false

  def handleElementRename(newElementName: String): PsiElement = {
    return this;
    //todo
  }

  def isReferenceTo(element: PsiElement): Boolean = resolve() == element

  import ResolveTargets._
  def processType(t: ScType, processor: BaseProcessor): Boolean = t match {
    case ScDesignatorType(e) if !e.isInstanceOf[ScTypeAlias] => //scala ticket 425
      e.processDeclarations(processor, ResolveState.initial, null, this)
    case ScPolymorphicType(ta, subst) => processType(subst.subst(ta.upperBound), processor)

    case p: ScParameterizedType => p.designated match {
        case ta : ScTypeAlias => processType(p.substitutor.subst(ta.upperBound), processor)
        case des => des.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, p.substitutor), null, this)
      }

    case ValType(name, _) => SyntheticClasses.get(getProject).byName(name) match {
      case Some(c) => c.processDeclarations(processor, ResolveState.initial, null, this)
    }

    case ScCompoundType(comp, decls, types) => {
      if (processor.kinds.contains(VAR) || processor.kinds.contains(VAL) || processor.kinds.contains(METHOD)) {
        for (decl <- decls) {
          for (declared <- decl.declaredElements) {
            if (!processor.execute(declared, ResolveState.initial)) return false
          }
        }
      }

      if (processor.kinds.contains(CLASS)) {
        for (t <- types) {
          if (!processor.execute(t, ResolveState.initial)) return false
        }
      }

      for (c <- comp) {
        if (!processType(c, processor)) return false
      }
      true
    }
    case ScSingletonType(path) => path.bind match {
      case Some(r) => r.element.processDeclarations(processor, ResolveState.initial, null, this)
      case _ => true
    }
    case _ => true //todo
  }

  def qualifier : Option[ScalaPsiElement]
}
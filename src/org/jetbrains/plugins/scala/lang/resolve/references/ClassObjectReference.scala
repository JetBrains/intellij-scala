package org.jetbrains.plugins.scala.lang.resolve.references
import org.jetbrains.plugins.scala.lang.psi.impl.top._

/**
* @author Ilya Sergey
*
*/

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.resolve.processors._
import com.intellij.openapi.util.TextRange

import org.jetbrains.plugins.scala.lang.psi.impl.top.defs._
import org.jetbrains.plugins.scala.lang.psi.impl.types._

class ClassObjectReference(myElement: PsiElement) extends ScalaClassReference(myElement) {


  /**
   * Returns the element which is the target of the reference.
   *
   * @return the target element, or null if it was not possible to resolve the reference to a valid target.
   */

  override def resolve: PsiElement = {
    val refName = getReferencedName
    if (refName != null) {
      ScalaResolveUtil.treeWalkUp(new ClassObjectResolveProcessor(refName), getElement, getElement, getElement)
    } else null
  }


  /**
   * Called when the reference target element has been renamed, in order to change the reference
   * text according to the new name.
   *
   * @param newElementName the new name of the target element.
   * @return the new underlying element of the reference.
   * @throws IncorrectOperationException if the rename cannot be handled for some reason.
   */
  override def handleElementRename(newElementName: String): PsiElement = {
    if (myElement.getFirstChild.isInstanceOf[ScImportEndId]) {
      import org.jetbrains.plugins.scala.lang.psi.impl._
      val newChildNode = ScalaPsiElementFactory.createExpressionFromText(newElementName,
              PsiManager.getInstance(myElement.getProject))
      myElement.getNode.replaceChild(myElement.getFirstChild.getNode, newChildNode)
      newChildNode.getPsi
    } else {
      null
    }
  }


  /**
  *  Returns non-qualified reference name
  *
  */
  override def getReferencedName ={
    myElement.getText
  }

}

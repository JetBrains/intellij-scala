package org.jetbrains.plugins.scala.lang.resolve.references

/** 
* @author ilyas
*/

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.resolve.processors._
import com.intellij.openapi.util.TextRange

import org.jetbrains.plugins.scala.lang.psi.impl.top.defs._
import org.jetbrains.plugins.scala.lang.psi.impl.types._
import org.jetbrains.plugins.scala.lang.psi.impl.primitives.ScIdentifier
import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements._
import org.jetbrains.plugins.scala.lang.psi.impl.top.defs.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.expressions.simpleExprs._
import org.jetbrains.plugins.scala.lang.typechecker.types._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._

class ScalaPropertySelectionReference(val myElement: PsiElement) extends PsiReference {


  /**
   * Returns the underlying (referencing) element of the reference.
   *
   * @return the underlying element of the reference.
   */
  def getElement = myElement

  /**
   * Returns the part of the underlying element which serves as a reference, or the complete
   * text range of the element if the entire element is a reference.
   *
   * @return Relative range in element
   */
  def getRangeInElement = {
    val index = myElement.getText.lastIndexOf(".")
    new TextRange(index + 1, myElement.getTextLength)
  }

  /**
   * Returns the element which is the target of the reference.
   *
   * @return the target element, or null if it was not possible to resolve the reference to a valid target.
   */

  def resolve: PsiElement = {
    if (myElement.getFirstChild.isInstanceOf[ScalaExpression] &&
    myElement.getFirstChild.asInstanceOf[ScalaExpression].getAbstractType != null){
      val absType = myElement.getFirstChild.asInstanceOf[ScalaExpression].getAbstractType
      absType.reduce match {
        // Define type of expression before dot
        case ValueType(ownType, _) if ownType != null => {
          // if method call  or closure variable
          val elem = myElement.getParent.isInstanceOf[ScMethodCallImpl]
          elem

          val candidates =
            if (myElement.getParent.isInstanceOf[ScMethodCallImpl]) {
              val argTypes = myElement.getParent.asInstanceOf[ScMethodCallImpl].getAllArguments.map((e: ScalaExpression) =>
                e.getAbstractType)
              ownType.getAllTemplateStatements.filter((stmt: PsiElement) => {
                stmt.isInstanceOf[ScFunction] &&
                stmt.asInstanceOf[ScFunction].getAbstractType(null).canBeAppliedTo(argTypes) &&
                stmt.asInstanceOf[ScReferenceIdContainer].getNames.exists((id: ScReferenceId) =>
                  (id.getName.equals(getReferencedName)))
              }).map((e: PsiElement) => e.asInstanceOf[ScFunction])
            } else {
              ownType.getAllTemplateStatements.filter((stmt: PsiElement) =>
                (stmt.isInstanceOf[ScFunction] && (stmt.asInstanceOf[ScFunction].getAllParams.length == 0) ||
                stmt.isInstanceOf[ScalaVariable] || stmt.isInstanceOf[ScalaValue]) &&
                stmt.asInstanceOf[ScReferenceIdContainer].getNames.exists((id: ScReferenceId) =>
                  id.getName.equals(getReferencedName))).map((e: PsiElement) => e.asInstanceOf[ScReferenceIdContainer])
            }
          if (candidates.length != 1) {
            Console.println("Found " + candidates.length + " candidates for resolve property " + getReferencedName +
            " in " + absType.reduce.getRepresentation)
            null
          } else {
            candidates.last.getNames.find((id: ScReferenceId) =>
              (id.getName.equals(getReferencedName))).get
          }
        }
        case _ => null
      }
    } else {
      null
    }
  }

  /**
   * Returns the name of the reference target element which does not depend on import statements
   * and other context (for example, the full-qualified name of the class if the reference targets
   * a Java class).
   *
   * @return the canonical text of the reference.
   */
  def getCanonicalText: String = ""

  /**
   * Called when the reference target element has been renamed, in order to change the reference
   * text according to the new name.
   *
   * @param newElementName the new name of the target element.
   * @return the new underlying element of the reference.
   * @throws IncorrectOperationException if the rename cannot be handled for some reason.
   */
  def handleElementRename(newElementName: String): PsiElement = {
    /*
        import org.jetbrains.plugins.scala.lang.psi.impl._
        val newChildNode = ScalaPsiElementFactory.createIdentifierFromText(newElementName,
            PsiManager.getInstance(myElement.getProject))
        myElement.getNode.replaceChild(myElement.getFirstChild.getNode, newChildNode)
        newChildNode.getPsi
    */ null
  }

  /**
  * Changes the reference so that it starts to point to the specified element. This is called,
  * for example, by the "Create Class from New" quickfix, to bind the (invalid) reference on
  * which the quickfix was called to the newly created class.
  *
  * @param element the element which should become the target of the reference.
  * @return the new underlying element of the reference.
  * @throws IncorrectOperationException if the rebind cannot be handled for some reason.
  */
  def bindToElement(element: PsiElement): PsiElement = {
    null
  }

  /**
   * Checks if the reference targets the specified element.
   *
   * @param element the element to check target for.
   * @return true if the reference targets that element, false otherwise.
   */
  def isReferenceTo(element: PsiElement): Boolean = {
    false
    /*
        val resolved = resolve
        if (element.equals(resolved)) return true

        var qName1: String = null
        if (resolved.isInstanceOf[PsiClass]) qName1 = resolve.asInstanceOf[PsiClass].getQualifiedName
        if (resolved.isInstanceOf[ScTmplDef]) qName1 = resolve.asInstanceOf[ScTmplDef].getQualifiedName
        var qName2: String = null
        if (element.isInstanceOf[PsiClass]) qName2 = element.asInstanceOf[PsiClass].getQualifiedName
        if (element.isInstanceOf[ScTmplDef]) qName2 = element.asInstanceOf[ScTmplDef].getQualifiedName

        qName1 != null && qName1.equals(qName2)
    */
  }

  /**
   * Returns the array of String, {@link PsiElement} and/or {@link com.intellij.psi.infos.CandidateInfo}
   * instances representing all identifiers that are visible at the location of the reference. The contents
   * of the returned array is used to build the lookup list for basic code completion. (The list
   * of visible identifiers must not be filtered by the completion prefix string - the
   * filtering is performed later by IDEA core.)
   *
   * @return the array of available identifiers.
   */
  def getVariants() = {
    new Array[java.lang.Object](0)
  }

  /**
  * Returns false if the underlying element is guaranteed to be a reference, or true
  * if the underlying element is a possible reference which should not be reported as
  * an error if it fails to resolve. For example, a text in an XML file which looks
  * like a full-qualified Java class name is a soft reference.
  *
  * @return true if the refence is soft, false otherwise.
  */
  def isSoft(): Boolean = {
    true
  }

  /**
  *  Returns non-qualified reference name
  *
  */
  def getReferencedName = {
    if (myElement.getText != null && myElement.getText.contains(".")) {
      val index = myElement.getText.lastIndexOf(".")
      myElement.getText.substring(index + 1)
    } else {
      ""
    }
  }

}
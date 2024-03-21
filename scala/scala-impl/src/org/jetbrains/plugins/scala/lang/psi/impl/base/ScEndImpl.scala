package org.jetbrains.plugins.scala.lang.psi.impl.base

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScBegin
import org.jetbrains.plugins.scala.lang.psi.api.base.ScEnd
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScEndImpl.Name
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiElementImpl}

class ScEndImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScEnd {
  override def begin: Option[ScBegin] = this.parentsInFile.findByType[ScBegin]

  override def keyword: PsiElement = getFirstChild

  override def tag: PsiElement = getLastChild

  override def getName: String = tag.getText

  override def setName(name: String): PsiElement = {
    tag.replace(ScalaPsiElementFactory.createIdentifier(name).getPsi)
  }

  override def getReference: PsiReference = this

  override def getElement: PsiElement = this

  override def getRangeInElement: TextRange = tag.getTextRangeInParent

  // Enable Rename and Find Usages, but don't highlight the reference as usage, SCL-19675
  override def resolve(): PsiElement = if (!tag.isIdentifier) null else {
    val target = ScalaPsiElementFactory.createScalaFileFromText(s"class $Name", this)(this).typeDefinitions.head
    target.context = this
    target
  }

  override def handleElementRename(newElementName: String): PsiElement = setName(newElementName)

  override def bindToElement(element: PsiElement): PsiElement = this

  override def isReferenceTo(element: PsiElement): Boolean = false

  override def isSoft: Boolean = true

  override def getCanonicalText: String = "ScEnd"

  override def toString: String = "End: " + getName
}

object ScEndImpl {
  private final val Name = "ScEndTarget2cf17ff3b2a54d14b64914496f02dc65" // Random unique ID

  object Target {
    /** @return ScEnd element of the target */
    def unapply(target: PsiElement): Option[ScEnd] = target match {
      case target: ScClass if target.name == Name => Some(target.getContext.asInstanceOf[ScEnd])
      case _ => None
    }
  }
}


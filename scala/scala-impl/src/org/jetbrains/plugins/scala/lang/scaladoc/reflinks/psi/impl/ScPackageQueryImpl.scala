package org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, ResolveResult}
import org.jetbrains.plugins.scala.extensions.{OptionExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScPackage
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.impl.{ScPackageImpl, ScalaPsiElementImpl, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.ScPackageQuery

class ScPackageQueryImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPackageQuery {

  override def getElement: PsiElement = this

  override def getRangeInElement: TextRange = packageToken.getTextRangeInParent

  override def multiResolveScala(incomplete: Boolean): Array[ScalaResolveResult] =
    resolvePackage().iterator.map(new ScalaResolveResult(_)).toArray

  override def resolvePackage(): Option[ScPackage] =
    this.parentOfType[ScPackaging].flatMap(p => ScPackageImpl.findPackage(p.fqn)(ScalaPsiManager.instance))

  override def resolve(): ScPackage = resolvePackage().orNull

  override def getCanonicalText: String = "package"

  override def handleElementRename(newElementName: String): PsiElement =
    throw new UnsupportedOperationException("Cannot rename package reference")

  override def bindToElement(element: PsiElement): PsiElement = this

  override def isReferenceTo(element: PsiElement): Boolean = element == resolve()

  override def isSoft: Boolean = false

  override def toString: String = s"ScPackageQuery"
}

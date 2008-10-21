package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.packaging

import api.ScalaFile
import api.toplevel.typedef.ScTypeDefinition
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.{PsiElement, PsiPackage}
import stubs.elements.wrappers.DummyASTNode
import stubs.ScPackageContainerStub;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode
                                                                          
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.base._

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

class ScPackageStatementImpl extends ScalaStubBasedElementImpl[ScPackageContainer] with ScPackageStatement{
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScPackageContainerStub) = {this(); setStub(stub)}

  def packagings: Seq[ScPackaging] = getParent match {
    case f: ScalaFile => List.fromArray(f.getPackagings)
    case _ => Seq.empty
  }

  def ownNamePart: String = getPackageName
  def prefix = ""

  def typeDefs: Seq[ScTypeDefinition] = getParent match {
    case f: ScalaFile => f.immediateTypeDefinitions
    case _ => Seq.empty
  }

  override def toString = "ScPackageStatement"

  def getPackageName = reference.qualName
}
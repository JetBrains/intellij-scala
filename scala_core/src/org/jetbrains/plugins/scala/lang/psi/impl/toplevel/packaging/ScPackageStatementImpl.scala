package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.packaging

import api.toplevel.typedef.ScTypeDefinition
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.{PsiElement, PsiPackage}
import stubs.elements.wrappers.ASTNodeWrapper
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

class ScPackageStatementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPackageStatement{

  def packagings: Seq[ScPackaging] = getParent match {
    case f: ScalaFile => List.fromArray(f.getPackagings)
    case _ => Seq.empty
  }

  def fqn: String = getPackageName

  def typeDefs: Seq[ScTypeDefinition] = getParent match {
    case f: ScalaFile => List.fromArray(f.getTypeDefinitions)
    case _ => Seq.empty
  }

  override def toString = "ScPackageStatement"

  def getPackageName: String = {
    val buffer = new _root_.scala.StringBuilder
    def append(ref : ScStableCodeReferenceElement) {
      val name = ref.refName
      ref.qualifier match {
        case None => buffer append name
        case Some(q) => {
          append(q)
          buffer.append ('.').append(name)
        }
      }
    }
    append (reference)
    buffer.toString
  }

  def getSyntheticPackage(fqn: String): PsiPackage = {
    null
  }
  
}

object ScPackageStatementImpl {
  def apply(stub: ScPackageContainerStub): ScPackaging = new ScPackagingImpl(new ASTNodeWrapper()) {
    setNode(null)
    setStubImpl(stub)
    override def getElementType = ScalaElementTypes.PACKAGE_STMT.asInstanceOf[IStubElementType[Nothing, Nothing]]
  }
}
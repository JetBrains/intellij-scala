package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import api.expr.ScNewTemplateDefinition
import com.intellij.openapi.editor.colors.TextAttributesKey
import api.toplevel.ScTyped
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.impl.light.LightElement
import com.intellij.util.IncorrectOperationException
import java.lang.String
import javax.swing.Icon
import com.intellij.openapi.vcs.FileStatus
import java.util.List
import com.intellij.psi._
import com.intellij.psi.scope.processor.MethodResolverProcessor
import com.intellij.psi.scope.PsiScopeProcessor
import psi.stubs.elements.wrappers.DummyASTNode
import psi.stubs.ScTypeDefinitionStub
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.tree.IElementType
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import synthetic.{PsiMethodFake, SyntheticNamedElement}
import types.{ScDesignatorType, ScSubstitutor, ScFunctionType, ScType}
import util.MethodSignatureBackedByPsiMethod

/**
 * @author Alexander Podkhalyuzin
 * Date: 20.02.2008
 */

class ScObjectImpl extends ScTypeDefinitionImpl with ScObject with ScTemplateDefinition {
  def this(node: ASTNode) = {this (); setNode(node)}

  def calcType() = new ScDesignatorType(this)

  def this(stub: ScTypeDefinitionStub) = {this (); setStub(stub); setNode(null)}

  override def toString: String = if (isPackageObject) "ScPackageObject" else "ScObject"

  override def getIconInner = if (isPackageObject) Icons.PACKAGE_OBJECT else Icons.OBJECT

  override def hasModifierProperty(name: String): Boolean = {
    if (name == "final") return true
    super[ScTypeDefinitionImpl].hasModifierProperty(name)
  }


  override def isPackageObject: Boolean = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScTypeDefinitionStub].isPackageObject
    } else findChildByType(ScalaTokenTypes.kPACKAGE) != null
  }

  override def isCase = hasModifierProperty("case")

  override def getContainingClass() = null

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    val proceed = super[ScTemplateDefinition].processDeclarations(processor, state, lastParent, place)
    if (!proceed) return false;
    true
  }

}

package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScSubstitutor
import api.base.{ScPrimaryConstructor, ScModifierList}
import api.statements.params.ScTypeParamClause
import com.intellij.psi.stubs.{StubElement, IStubElementType}
import com.intellij.util.ArrayFactory
import stubs.elements.wrappers.DummyASTNode
import stubs.ScTypeDefinitionStub
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiMethod, PsiElement, PsiNamedElement, PsiModifierList};
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import org.jetbrains.plugins.scala.icons.Icons

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

/**
 * @author Alexander.Podkhalyuzin
 */

class ScClassImpl extends ScTypeDefinitionImpl with ScClass with ScTypeParametersOwner with ScTemplateDefinition {
 def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTypeDefinitionStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ScClass"

  override def getIconInner = Icons.CLASS

  def constructor: Option[ScPrimaryConstructor] = {
    val stub = getStub
    if (stub != null) {
      val array = stub.getChildrenByType(ScalaElementTypes.PRIMARY_CONSTRUCTOR, new ArrayFactory[ScPrimaryConstructor] {
        def create(count: Int): Array[ScPrimaryConstructor] = new Array[ScPrimaryConstructor](count)
      })
      if (array.length == 0) {
        return None
      } else {
        return Some(array.apply(0))
      }
    }
    findChild(classOf[ScPrimaryConstructor])
  }

  def parameters = constructor match {
    case Some(c) => c.parameters
    case None => Seq.empty
  }

  override def members() = constructor match {
    case Some(c) => super.members ++ Seq.singleton(c)
    case _ => super.members
  }

  import com.intellij.psi.{scope, PsiElement, ResolveState}
  import scope.PsiScopeProcessor
  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    if (!super[ScTemplateDefinition].processDeclarations(processor, state, lastParent, place)) return false

    for (p <- parameters) {
      if (!processor.execute(p, state)) return false
    }

    super[ScTypeParametersOwner].processDeclarations(processor, state, lastParent, place)
  }

  override def isCase: Boolean = hasModifierProperty("case")

  override def getAllMethods: Array[PsiMethod] = {
    constructor match {
      case Some(c) => Array[PsiMethod](c) ++ super.getAllMethods
      case _ => super.getAllMethods
    }
  }

  override def getConstructors: Array[PsiMethod] = getMethods.filter(_.isConstructor)
}
package org.jetbrains.plugins.scala.lang.psi.api.statements


import base.patterns.ScReferencePattern
import impl.toplevel.typedef.TypeDefinitionMembers.MethodNodes
import impl.toplevel.typedef.{TypeDefinitionMembers, MixinNodes}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import com.intellij.psi._

import psi.stubs.ScFunctionStub
import toplevel.templates.ScTemplateBody
import types._

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 * Time: 9:45:38
 */

//some functions are not PsiMethods and are e.g. not visible from java
//see ScSyntheticFunction
trait ScFun extends ScTypeParametersOwner {
  def retType: ScType

  def paramTypes: Seq[ScType]

  def typeParameters: Seq[ScTypeParam]
}

trait ScFunction extends ScalaPsiElement with ScNamedElement with ScMember with ScTypeParametersOwner
        with PsiMethod with ScParameterOwner with ScDocCommentOwner with ScTyped with ScDeclaredElementsHolder with ScAnnotationsHolder {
  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  def paramClauses: ScParameters

  def returnTypeElement: Option[ScTypeElement] = {
    this match {
      case st: ScalaStubBasedElementImpl[_] => {
        val stub = st.getStub
        if (stub != null) {
          return stub.asInstanceOf[ScFunctionStub].getReturnTypeElement
        }
      }
      case _ =>
    }
    findChild(classOf[ScTypeElement])
  }

  def returnType: ScType

  def declaredType: ScType = returnTypeElement match {
    case Some(rte) => rte.cashedType
    case None => Nothing
  }

  def clauses: Option[ScParameters] = Some(paramClauses)

  def parameters: Seq[ScParameter]

  def paramTypes: Seq[ScType] = parameters.map{_.calcType}

  def declaredElements = Seq.singleton(this)

  def superMethods: Seq[PsiMethod]

  def superMethod: Option[PsiMethod]

  def superSignatures: Seq[FullSignature]

}
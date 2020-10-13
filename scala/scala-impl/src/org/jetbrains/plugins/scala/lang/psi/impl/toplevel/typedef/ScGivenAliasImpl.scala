package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions.{ifReadAllowed, _}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenAlias
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScFunctionElementType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}

class ScGivenAliasImpl(stub: ScFunctionStub[ScGivenAlias],
                       nodeType: ScFunctionElementType[ScGivenAlias],
                       node: ASTNode)
  extends ScFunctionImpl(stub, nodeType, node)
    with ScGivenImpl
    with ScGivenAlias {

  override def toString: String = "ScGivenAlias: " + ifReadAllowed(name)("")
  override def returnType: TypeResult = Failure(ScalaBundle.message("scgivenaliasimpl.returntype.not.yet.implemented"))

  override protected def typeElementForAnonymousName: Option[ScTypeElement] =
    byPsiOrStub(findChild(classOf[ScTypeElement]))(_.typeElement)


  private lazy val syntheticEmptyParameterList =
    ScalaPsiElementFactory.createParamClausesWithContext("", this, this.getFirstChild)

  override def paramClauses: ScParameters = {
    super.paramClauses.nullSafe.getOrElse(syntheticEmptyParameterList)
  }
}

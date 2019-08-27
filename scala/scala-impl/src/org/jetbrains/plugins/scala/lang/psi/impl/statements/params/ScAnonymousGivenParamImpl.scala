package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScAnonymousGivenParam
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParameterStub

class ScAnonymousGivenParamImpl private(stub: ScParameterStub, node: ASTNode)
  extends ScParameterImpl(stub, ScalaElementType.ANONYMOUS_GIVEN_PARAM, node)
    with ScAnonymousGivenParam {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScParameterStub) = this(stub, null)

  override def toString: String = s"Anonymous given parameter: ${typeElement.fold("unknown type")(_.toString)}"

  override def getTypeElement: Null = null

  override def typeElement: Option[ScTypeElement] = byPsiOrStub(findChild(classOf[ScTypeElement]))(_.typeElement)

  override def isRepeatedParameter: Boolean = false

  override def isCallByNameParameter: Boolean = false

  override def baseDefaultParam: Boolean = false

  override def getActualDefaultExpression: Option[ScExpression] = None

  override val nameId: PsiElement = null

  override def name: String = "_"
}

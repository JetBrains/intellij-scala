package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.psi.PsiElement
import javax.swing.Icon
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameterClause, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenInstance

trait ScGivenInstanceImpl extends ScGivenInstance with ScalaPsiElement {

  override def nameId: PsiElement = findFirstChildByType(ScalaTokenTypes.tIDENTIFIER)

  override def isAnonymous: Boolean = nameId == null

  override def setName(name: String): PsiElement = {
    if (isAnonymous) {
      ???
    } else super.setName(name)
    this
  }

  override def givenParameterClausesElement: Option[ScParameters] =
    findChild(classOf[ScParameters])

  override def givenParameterClauses: Seq[ScParameterClause] =
    givenParameterClausesElement.fold(Seq.empty[ScParameterClause])(_.clauses)

  override protected final def baseIcon: Icon =
    icons.Icons.CLASS

  override def declaredElements: Seq[ScTypedDefinition] = Seq(this)
}

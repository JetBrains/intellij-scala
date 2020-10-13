package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiNamedElement
import javax.swing.Icon
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenDefinition
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType

class ScGivenDefinitionImpl(stub:      ScTemplateDefinitionStub[ScGivenDefinition],
                            nodeType:  ScTemplateDefinitionElementType[ScGivenDefinition],
                            node:      ASTNode,
                            debugName: String)
  extends ScTypeDefinitionImpl(stub, nodeType, node, debugName)
    with ScGivenImpl
    with ScGivenDefinition {
  override protected def baseIcon: Icon = Icons.CLASS // todo: better icon ?

  override protected def targetTokenType: ScalaTokenType = ScalaTokenType.GivenKeyword

  override def declaredElements: Seq[PsiNamedElement] = Seq(this)

  override def isObject: Boolean = givenTypeParamClause.isEmpty && givenParameters.isEmpty

  override protected def typeElementForAnonymousName: Option[ScTypeElement] =
    extendsBlock.toOption
      .flatMap(_.templateParents)
      .flatMap(_.constructorInvocation)
      .flatMap(_.typeElement.toOption)
}

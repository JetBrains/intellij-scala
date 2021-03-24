package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.caches.ModTracker

import javax.swing.Icon
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenDefinition
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.macroAnnotations.Cached

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

  override def isObject: Boolean = typeParametersClause.isEmpty && clauses.isEmpty

  override def nameInner: String = {
    val explicitName = nameElement.map(_.getText)
    val typeElements = extendsBlock.templateParents.toSeq.flatMap(_.typeElements)

    explicitName
      .getOrElse(ScalaPsiUtil.generateGivenOrExtensionName(typeElements: _*))
  }

  @Cached(ModTracker.anyScalaPsiChange, this)
  override def clauses: Option[ScParameters] =
    findChildByType(ScalaElementType.PARAM_CLAUSES)

  override def parameters: Seq[ScParameter] =
    clauses.fold(Seq.empty[ScParameter])(_.params)
}

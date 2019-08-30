package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGivenDefinition, ScGivenInstance, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

class ScGivenDefinitionImpl(stub: ScTemplateDefinitionStub[ScGivenDefinition],
                            nodeType: ScTemplateDefinitionElementType[ScGivenDefinition],
                            node: ASTNode)
  extends ScTypeDefinitionImpl(stub, nodeType, node, ScalaTokenType.Given)
    with ScGivenInstanceImpl
    with ScGivenDefinition {

  override def flavor: ScGivenInstance.Flavor =
    if (hasCollectiveParam) ScGivenInstance.CollectiveExtMethod
    else ScGivenInstance.GivenImplementation

  override def hasCollectiveParam: Boolean = collectiveExtensionParamClause.isDefined

  override def collectiveExtensionParamClause: Option[ScParameterClause] = {
    findChild(classOf[ScParameterClause])
  }

  override def templateParents: Option[ScTemplateParents] =
    findChild(classOf[ScTemplateParents])

  override def desugaredElement: Option[ScTemplateDefinition] = cachedDesugared

  @Cached(ModCount.getBlockModificationCount, this)
  private def cachedDesugared: Option[ScTemplateDefinition] = {
    val name = "given_" + getTextOffset
    // TODO: desugar given parameter
    val objectKind = collectiveExtensionParamClause
      .fold("object " + name) { collectiveParamClause =>
        s"class $name${collectiveParamClause.getText}"
      }

    val parents = templateParents
      .map(_.typeElements.map(_.getText))
      .map(_.mkString(" extends ", " with ", ""))
      .getOrElse("")

    val text = s"implicit $objectKind $parents ${physicalExtendsBlock.getText}"

    try Some(
      ScalaPsiElementFactory.createObjectWithContext(text, getContext, this).
        setDesugared(actualElement = this)
    ) catch {
      // if the body contains unparsable stuff we cannot desugar
      case _: IncorrectOperationException => None
    }
  }
}

package org.jetbrains.plugins.scala.lang.psi.impl.toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType.EnumKeyword
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType

final class ScEnumImpl(stub: ScTemplateDefinitionStub[ScClass],
                       nodeType: ScTemplateDefinitionElementType[ScClass],
                       node: ASTNode,
                       debugName: String)
  extends ScClassImpl(stub, nodeType, node, debugName) with ScEnum {

  override def cases: Seq[ScEnumCase] =
    extendsBlock.cases.flatMap(_.declaredElements)

  override def processDeclarationsForTemplateBody(processor: PsiScopeProcessor, oldState: ResolveState, lastParent: PsiElement, place: PsiElement): Boolean = {
    val continue = cases.forall { aCase =>
      processor.execute(aCase, oldState) && ScalaPsiUtil.getCompanionModule(aCase).forall(processor.execute(_, oldState))
    }

    continue && super.processDeclarationsForTemplateBody(processor, oldState, lastParent, place)
  }

  override def hasModifierProperty(name: String): Boolean =
    name == "final" || name == "abstract" || super.hasModifierProperty(name)

  override def hasModifierPropertyScala(name: String): Boolean =
    name == "sealed" || name == "abstract" || super.hasModifierPropertyScala(name)

  //noinspection TypeAnnotation
  override protected def targetTokenType = EnumKeyword

  //noinspection TypeAnnotation
  override protected def baseIcon = Icons.ENUM

  override protected def keywordTokenType: IElementType = ScalaTokenType.EnumKeyword

  override def namedTag: Option[ScNamedElement] = Some(this)
}

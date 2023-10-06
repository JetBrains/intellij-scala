package org.jetbrains.plugins.scala.lang.psi.impl.toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, ResolveState}
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.caches.{ModTracker, cachedInUserData}
import org.jetbrains.plugins.scala.extensions.StubBasedExt
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType.EnumKeyword
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScEnum, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScNamedBeginImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType

final class ScEnumImpl(stub: ScTemplateDefinitionStub[ScEnum],
                       nodeType: ScTemplateDefinitionElementType[ScEnum],
                       node: ASTNode,
                       debugName: String)
  extends ScTypeDefinitionImpl(stub, nodeType, node, debugName)
    with ScEnum
    with ScNamedBeginImpl {

  override def constructor: Option[ScPrimaryConstructor] =
    this.stubOrPsiChild(ScalaElementType.PRIMARY_CONSTRUCTOR)

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

  override protected def endParent: Option[PsiElement] = extendsBlock.templateBody
}

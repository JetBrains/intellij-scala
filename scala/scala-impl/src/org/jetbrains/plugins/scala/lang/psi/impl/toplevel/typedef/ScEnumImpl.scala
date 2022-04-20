package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.caches.ModTracker
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType.EnumKeyword
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScEnum, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScNamedBeginImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData

final class ScEnumImpl(stub: ScTemplateDefinitionStub[ScEnum],
                       nodeType: ScTemplateDefinitionElementType[ScEnum],
                       node: ASTNode,
                       debugName: String)
  extends ScTypeDefinitionImpl(stub, nodeType, node, debugName)
    with ScEnum
    with ScNamedBeginImpl {

  override def cases: Seq[ScEnumCase] =
    extendsBlock.cases.flatMap(_.declaredElements)

  private[this] def syntheticClassText = {
    val typeParametersText        = typeParametersClause.fold("")(_.getTextByStub)
    val supersText                = superTypes.map(_.canonicalText).mkString(" with ")
    val constructorText           = constructor.fold("")(_.getText)
    val secondaryConstructorsText = secondaryConstructors.map(_.getText).mkString("\n")
    val derivesText               = derivesClause.fold("")(_.getText)

    s"""
       |sealed abstract class $name$typeParametersText$constructorText extends $supersText $derivesText {
       |  $secondaryConstructorsText
       |}
       |""".stripMargin
  }

  @CachedInUserData(this, ModTracker.libraryAware(this))
  override def syntheticClass: Option[ScTypeDefinition] = {
    val cls = ScalaPsiElementFactory.createTypeDefinitionWithContext(syntheticClassText, this.getContext, this)
    cls.originalEnumElement        = this
    cls.syntheticNavigationElement = this
    Option(cls)
  }

  //noinspection TypeAnnotation
  override protected def targetTokenType = EnumKeyword

  //noinspection TypeAnnotation
  override protected def baseIcon = icons.Icons.ENUM

  override protected def keywordTokenType: IElementType = ScalaTokenType.EnumKeyword

  override def namedTag: Option[ScNamedElement] = Some(this)

  override protected def endParent: Option[PsiElement] = extendsBlock.templateBody
}

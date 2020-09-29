package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiClass, PsiModifierList}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScExtensionStub


class ScExtensionImpl(@Nullable stub: ScExtensionStub, @Nullable node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.Extension, node) with ScExtension {

  override def toString: String = "Extension on " + targetTypeElement.fold("<unknown>")(_.getText)

  override def targetTypeElement: Option[ScTypeElement] =
    parameters.headOption.flatMap(_.typeElement)

  override def extensionBody: Option[ScTemplateBody] =
    findChildByType[ScTemplateBody](ScalaElementType.TEMPLATE_BODY).toOption

  override def extensionMethods: Seq[ScFunctionDefinition] =
    extensionBody.fold(Seq.empty[ScFunctionDefinition])(_.functions.filterByType[ScFunctionDefinition])

  override def parameters: Seq[ScParameter] = clauses.flatMap(_.clauses.headOption.flatMap(_.parameters.headOption)).toSeq

  override def clauses: Option[ScParameters] =
    findChildByType[ScParameters](ScalaElementType.PARAM_CLAUSES).toOption

  override def getContainingClass: PsiClass = null

  override def getModifierList: PsiModifierList = null

  override def hasModifierProperty(name: String): Boolean = false
}


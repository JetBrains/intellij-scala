package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenAlias
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTypeElementOwnerStub
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}

trait ScGivenAliasDeclarationOrDefinitionImpl extends ScFunction
  with ScGivenImpl
  with ScGivenAlias {

  self: ScalaStubBasedElementImpl[_, _ <: ScTypeElementOwnerStub[_]] =>

  override def returnType: TypeResult =
    typeElement match {
      case Some(te) => te.`type`()
      case None => Failure(ScalaBundle.message("no.type.element.found", getText))
    }

  override def typeElement: Option[ScTypeElement] =
    byPsiOrStub(findChildByClassScala(classOf[ScTypeElement]).toOption)(_.typeElement)

  override protected def nameInner: String = {
    val explicitName = nameElement.map(_.getText)

    explicitName
      .getOrElse(ScalaPsiUtil.generateGivenName(typeElement.toSeq: _*))
  }


  override def nameId: PsiElement = {
    // TODO: returning this is a hack to not return null and has to be improved later
    //       see SCL-21867 for further details
    nameElement.orElse(typeElement).getOrElse(this)
  }
}

package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.psi.PsiModifier
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}

object UnusedDeclarationVerboseProblemInfoMessage {

  def apply(unusedElement: ScNamedElement): String = {
    val isPrivate = unusedElement match {
      case scMember: ScMember => scMember.isPrivate
      case scReferencePattern: ScReferencePattern =>
        Option(scReferencePattern.getContext)
          .flatMap(context => Option(context.getContext))
          .collect { case owner: ScModifierListOwner => owner }
          .exists(_.hasModifierPropertyScala(PsiModifier.PRIVATE))
      case _ => false
    }

    val name = unusedElement.nameId.getText

    val message = unusedElement match {
      case _: ScParameter =>
        s"Parameter '$name' is not used in either this method or any of its derived methods"
      case _: ScObject =>
        s"Object '$name' is never used"
      case _: ScClass =>
        s"Class '$name' is never used"
      case referencePattern: ScReferencePattern if referencePattern.isVal =>
         s"Value '$name' is never used"
      case referencePattern: ScReferencePattern if referencePattern.isVar =>
        s"Variable '$name' is never used"
      case _: ScFunctionDefinition =>
        s"Function definition '$name' is never used"
      case _ =>
        s"Declaration '$name' is never used"
    }

    if (isPrivate) {
      "Private " + message.charAt(0).toLower + message.substring(1)
    } else {
      message
    }
  }
}

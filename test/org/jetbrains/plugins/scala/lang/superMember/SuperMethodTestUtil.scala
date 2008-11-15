package org.jetbrains.plugins.scala.lang.superMember

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiNamedElement, PsiFile}
import com.jniwrapper.F
import psi.api.statements.ScFunction
import psi.api.toplevel.ScNamedElement
import psi.api.toplevel.typedef.{ScTypeDefinition, ScMember, ScTemplateDefinition}
import psi.types.FullSignature

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.11.2008
 */

object SuperMethodTestUtil {
  def transform(myFile: PsiFile, offset: Int): String = {
    val el = myFile.findElementAt(offset)
    val member = PsiTreeUtil.getParentOfType(el, classOf[ScMember], false)
    member match {
      case method: ScFunction => {
        val clazz = (method.getContainingClass : ScTemplateDefinition).supers.apply(0)
        val signs = method.superSignatures
        val res: StringBuilder = new StringBuilder("")
        for (sign <- signs) {
          res.append(sign.clazz.getQualifiedName + "." +
          (sign.element match {
            case x: PsiNamedElement => x.getName
            case _ => "Something"
          }) + "\n")
        }
        return if (res.toString == "") "" else res.substring(0, res.length - 1).toString
      }
      case _ => return "Not implemented test"
    }
  }
}
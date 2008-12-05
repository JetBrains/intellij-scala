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
    var resa = ""
    val el = myFile.findElementAt(offset)
    val member = PsiTreeUtil.getParentOfType(el, classOf[ScMember], false)
    member match {
      case method: ScFunction => {
        val signs = method.superSignatures
        val res: StringBuilder = new StringBuilder("")
        for (sign <- signs) {
          res.append(sign.clazz.getQualifiedName + "." +
          (sign.element match {
            case x: PsiNamedElement => x.getName
            case _ => "Something"
          }) + "\n")
        }
        resa = if (res.toString == "") "" else res.substring(0, res.length - 1).toString
      }
      case _ => resa = "Not implemented test"
    }
    println(resa)
    return resa
  }
}
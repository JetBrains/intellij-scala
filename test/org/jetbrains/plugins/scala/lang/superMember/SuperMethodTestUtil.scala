package org.jetbrains.plugins.scala
package lang
package superMember

import com.intellij.psi.util.PsiTreeUtil
import psi.api.statements.ScFunction
import psi.api.toplevel.typedef.{ScMember}
import psi.ScalaPsiUtil
import com.intellij.psi.{PsiMember, PsiNamedElement, PsiFile}
import extensions.toPsiClassExt

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
          val s = sign.namedElement match {
            case Some(named) =>
              ScalaPsiUtil.nameContext(named) match {
                case member: PsiMember =>
                  val clazz = member.getContainingClass
                  if (clazz != null)
                    clazz.qualifiedName + "."
                  else ""
                case _ => ""
              }
            case _ => ""
          }
          res.append(s + (sign.namedElement match {
                    case Some(x: PsiNamedElement) => x.getName
                    case _ => "Something"
                  }) + "\n")
        }
        resa = if (res.toString == "") "" else res.substring(0, res.length - 1).toString
      }
      case _ => resa = "Not implemented test"
    }
    println(resa)
    resa
  }
}
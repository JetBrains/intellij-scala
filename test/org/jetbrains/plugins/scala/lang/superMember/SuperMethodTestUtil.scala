package org.jetbrains.plugins.scala
package lang
package superMember

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiFile, PsiMember, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

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
      case method: ScFunction =>
        val signs = method.superSignaturesIncludingSelfType
        val res: StringBuilder = new StringBuilder("")
        for (sign <- signs) {
          val s = ScalaPsiUtil.nameContext(sign.namedElement) match {
            case member: PsiMember =>
              val clazz = member.containingClass
              if (clazz != null)
                clazz.qualifiedName + "."
              else ""
            case _ => ""
          }
          res.append(s + (sign.namedElement match {
                    case x: ScNamedElement => x.name
                    case x: PsiNamedElement => x.getName
                  }) + "\n")
        }
        resa = if (res.toString == "") "" else res.substring(0, res.length - 1).toString
      case _ => resa = "Not implemented test"
    }
    resa
  }
}
package org.jetbrains.plugins.scala
package lang
package superMember

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiFile, PsiMember}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

import scala.collection.mutable

object SuperMethodTestUtil {
  def transform(myFile: PsiFile, offset: Int): String = {
    var resa = ""
    val el = myFile.findElementAt(offset)
    val member = PsiTreeUtil.getParentOfType(el, classOf[ScMember], false)
    member match {
      case method: ScFunction =>
        val signs = method.superSignaturesIncludingSelfType
        val res = new mutable.StringBuilder("")
        for (sign <- signs) {
          val s = sign.namedElement.nameContext match {
            case member: PsiMember =>
              val clazz = member.containingClass
              if (clazz != null)
                clazz.qualifiedName + "."
              else ""
            case _ => ""
          }
          res.append(s + sign.namedElement.name + "\n")
        }
        resa = if (res.toString == "") "" else res.substring(0, res.length - 1)
      case _ => resa = "Not implemented test"
    }
    resa
  }
}
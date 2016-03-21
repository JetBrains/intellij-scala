package org.jetbrains.plugins.scala.lang.psi.api

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScMethodType

import scala.annotation.tailrec

/**
  * @author adkozlov
  */
package object base {

  implicit class ScMethodLikeExt(val method: ScMethodLike) extends AnyVal {
    def nestedMethodType(n: Int, `type`: Option[ScType] = None): Option[ScType] = {
      nested(method.methodType(`type`), n)
    }

    /**
      * Unwraps the method type corresponding to the parameter secion at index `n`.
      *
      * For example:
      *
      * def foo(a: Int)(b: String): Boolean
      *
      * nested(foo.methodType(...), 1) => MethodType(retType = Boolean, params = Seq(String))
      */
    @tailrec
    private def nested(`type`: ScType, n: Int): Option[ScType] =
      if (n == 0) Some(`type`)
      else `type` match {
        case methodType: ScMethodType => nested(methodType.returnType, n - 1)
        case _ => None
      }
  }

}

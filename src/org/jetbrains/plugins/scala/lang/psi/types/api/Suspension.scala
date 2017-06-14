package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * @author ven
  */
sealed trait Suspension {
  def v: ScType
}

object Suspension {
  private class Lazy(private var fun: () => ScType) extends Suspension {
    def this(`type`: ScType) = this({ () => `type` })

    lazy val v: ScType = {
      val res = fun()
      fun = null
      res
    }
  }

  private case class Strict(v: ScType) extends Suspension

  def apply(fun: () => ScType): Suspension = new Lazy(fun)
  def apply(v: ScType): Suspension = Strict(v)
}
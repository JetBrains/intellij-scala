package org.jetbrains.plugins.scala.lang.psi.types

/**
  * Nikolay.Tropin
  * 01-Feb-18
  */
package object recursiveUpdate {
  trait Update extends (ScType => AfterUpdate)

  sealed trait AfterUpdate

  object AfterUpdate {
    case class ReplaceWith(scType: ScType) extends AfterUpdate

    case object ProcessSubtypes extends AfterUpdate

    case object Stop extends AfterUpdate
  }
}

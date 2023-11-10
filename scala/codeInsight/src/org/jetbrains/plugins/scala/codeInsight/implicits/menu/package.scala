package org.jetbrains.plugins.scala.codeInsight.implicits

import org.jetbrains.plugins.scala.annotator.hints.Hint.MenuProvider

package object menu {
  val ImplicitConversion: MenuProvider = MenuProvider("ImplicitConversionMenu")
  val ImplicitArguments: MenuProvider = MenuProvider("ImplicitArgumentsMenu")
  val ExplicitArguments: MenuProvider = MenuProvider("ExplicitArgumentsMenu")
}

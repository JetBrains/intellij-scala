package org.jetbrains.plugins.scala.refactoring.introduceVariable
/**
 * @author Aleksander Podkhalyuzin
 * @date 05.04.2009
 */

object IntroduceVariableTestUtil {
  def extract1[T,U](x: (T, U)): T = x._1
  def extract2[T,U](x: (T, U)): U = x._2
}
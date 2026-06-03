package org.jetbrains.plugins.scala.codeInsight.intention.lists

sealed trait SplitJoinTestType {
  def isJoin: Boolean = this == SplitJoinTestType.Join
}

object SplitJoinTestType {
  case object Split extends SplitJoinTestType
  case object Join extends SplitJoinTestType
}

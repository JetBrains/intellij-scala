package org.jetbrains.plugins.scala.codeInsight.intention.lists

sealed trait SplitJoinTestType { self =>
  def isJoin: Boolean = self == SplitJoinTestType.Join
}

object SplitJoinTestType {
  case object Split extends SplitJoinTestType
  case object Join extends SplitJoinTestType
}

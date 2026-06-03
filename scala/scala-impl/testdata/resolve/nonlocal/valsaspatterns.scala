class ResolveTargets

object ResolveTargets {
  val METHOD = new ResolveTargets
  val VAR =    new ResolveTargets
}

import ResolveTargets._

class ResolveTargetsUser {
    val m = <ref>VAR
}
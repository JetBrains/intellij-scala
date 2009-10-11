trait Symbols {
  trait Symbol
}

object Types {
  self: Symbols =>
                  // As of 11.10.2009, this would multi-resolve to scala.Symbol and Symbols.Symbol.
  trait B extends <ref>Symbol
}
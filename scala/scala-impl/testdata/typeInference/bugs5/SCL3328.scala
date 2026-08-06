object Handler {
  def isDefinedAt(t : Throwable) = false

  def apply(t: Throwable) {}
}

/*start*/try {
  1
} catch Handler/*end*/
//AnyVal
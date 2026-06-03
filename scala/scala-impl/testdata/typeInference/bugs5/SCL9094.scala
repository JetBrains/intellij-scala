object SCL9094 {
  def namesToSummaries(names: Iterable[String], summaryPool: Boolean) = {
    val namePattern = """([a-zA-Z]+\d+)([a-z])?""".r
    names collect /*start*/{ name =>
      name match {
        case namePattern(summaryName, summaryType) if (summaryPool) => {
          var sType = summaryType
          if (sType == null || !(sType == "b" || sType == "l")) sType = "b"
          summaryPool
        }
      }
    }/*end*/
  }
}
//PartialFunction[String, NotInferedB]
package org.jetbrains.plugins.scala.editor.importOptimizer

/**
 * compareImports and compareNames are copy-pasted from [[org.scalastyle.scalariform.ImportOrderChecker]]
 */
object ScalastyleImportsUtil {
  val importOrderChecker = "org.scalastyle.scalariform.ImportOrderChecker"

  /**
    * Compares two import statements, comparing each component of the import separately.
    *
    * The import statements can end with a dangling `.`, meaning they're the start of a
    * multi-import block.
    */
  def compareImports(imp1: String, imp2: String): Int = {
    val imp1Components = imp1.split("[.]")
    val imp2Components = imp2.split("[.]")
    val max = math.min(imp1Components.size, imp2Components.size)
    for (i <- 0 until max) {
      val comp1 = imp1Components(i)
      val comp2 = imp2Components(i)
      val result = compareNames(comp1, comp2, isImport = true)
      if (result != 0) {
        return result
      }
    }

    // At this point, there is still a special case: where one import is a multi-import block
    // (and, thus, has no extra components) and another is a wildcard; the wildcard should come
    // first.
    val diff = imp1Components.size - imp2Components.size
    if (diff == -1 && imp1.endsWith(".") && imp2Components.last == "_") {
      1
    } else if (diff == 1 && imp2.endsWith(".") && imp1Components.last == "_") {
      -1
    } else {
      diff
    }
  }

  /**
    * Compares two strings that represent a single imported artifact; this considers lower-case
    * names as being "lower" than upper case ones.
    *
    * @param name1 First name.
    * @param name2 Second name.
    * @param isImport If true, orders names according to the import statement rules:
    *                 "_" should come before other names, and capital letters should come
    *                 before lower case ones. Otherwise, do the opposite, which are the ordering
    *                 rules for names within a multi-import block.
    */
  def compareNames(name1: String, name2: String, isImport: Boolean): Int = {
    if (name1 != "_") {
      if (name2 == "_") {
        -1 * compareNames(name2, name1, isImport)
      } else {
        val isName1UpperCase = Character.isUpperCase(name1.codePointAt(0))
        val isName2UpperCase = Character.isUpperCase(name2.codePointAt(0))

        if (isName1UpperCase == isName2UpperCase) {
          name1.compareToIgnoreCase(name2)
        } else {
          if (isName1UpperCase && !isImport) 1 else -1
        }
      }
    } else {
      if (isImport) -1 else 1
    }
  }

  val nameOrdering: Ordering[String] = Ordering.fromLessThan(compareNames(_, _, isImport = false) < 0)
}

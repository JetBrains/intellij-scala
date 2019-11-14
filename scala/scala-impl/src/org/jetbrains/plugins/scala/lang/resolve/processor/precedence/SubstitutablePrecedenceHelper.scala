package org.jetbrains.plugins.scala.lang
package resolve
package processor
package precedence

trait SubstitutablePrecedenceHelper extends PrecedenceHelper {

  private var knownPriority: Option[Int] = None

  def runWithPriority(priority: Int)(body: => Unit): Unit = {
    val oldPriority = knownPriority
    knownPriority = Some(priority)
    try {
      body
    } finally {
      knownPriority = oldPriority
    }
  }

  protected def isPredefPriority: Boolean =
    knownPriority == precedenceTypes.defaultImportPrecedence("scala.Predef")

  override protected def precedence(result: ScalaResolveResult): Int =
    knownPriority.getOrElse(super.precedence(result))
}

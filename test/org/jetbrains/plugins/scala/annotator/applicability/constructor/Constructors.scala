package org.jetbrains.plugins.scala
package annotator.applicability
package constructor

trait Constructors extends Applicability {
  override def format(definition: String, application: String) = {
    "class F" + definition + " {}; " + "new F" + application
  }
}

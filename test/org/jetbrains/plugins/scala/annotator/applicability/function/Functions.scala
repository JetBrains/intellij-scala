package org.jetbrains.plugins.scala
package annotator.applicability
package function

trait Functions extends Applicability { 
  override def format(definition: String, application: String) = {
    "def f" + definition + " {}; " + "f" + application
  }
}

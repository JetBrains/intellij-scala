package org.jetbrains.plugins.scala
package annotator.applicability

import lang.psi.types._

/**
 * Pavel.Fatin, 18.05.2010
 */

class ImplicitTest extends Applicability {
  def testFoo {
//    assertProblems("(implicit a: A)", "()") {
//      case MissedImplicitParameter(Parameter("a")) :: Nil =>
//    }
  }  
}
package org.jetbrains.plugins.scala
package codeInspection
package varCouldBeValInspection

import com.intellij.codeInspection._
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool
import com.intellij.psi.PsiElementVisitor

// This is checked in ScalaUnusedSymbolPass, the inspection is to allow this to be
// turned on/off in the Inspections settings.
class VarCouldBeValInspection extends LocalInspectionTool with UnfairLocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new PsiElementVisitor {}
  }
}

object VarCouldBeValInspection {
  val ShortName: String = "VarCouldBeVal"
}

// TODO Test
//  def method {
//    val a = 1
//    a.+=(2) // re-assignment to val
//    a += 2  // re-assignment to val
//
//    var b = 1
//    b.+=(2)
//
//    var c = 1
//    c += 2
//
//    var d = 1 // var could be val
//    d + 1
//  }

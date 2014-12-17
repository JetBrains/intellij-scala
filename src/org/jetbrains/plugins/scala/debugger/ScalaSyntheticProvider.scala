package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.engine.SyntheticTypeComponentProvider
import com.sun.jdi.{Method, ReferenceType, TypeComponent}

/**
 * Nikolay.Tropin
 * 2014-12-03
 */
class ScalaSyntheticProvider extends SyntheticTypeComponentProvider {
  override def isSynthetic(typeComponent: TypeComponent): Boolean = {
    typeComponent match {
      case m: Method if m.isConstructor && isAnonFun(m.declaringType()) => true
      case m: Method if isSpecialization(m) => true
      case m: Method if isDefaultArg(m) => true
      case _ => false
    }
  }

  private def isAnonFun(refType: ReferenceType): Boolean = {
    short(refType.name).contains("$anonfun")
  }

  private def isSpecialization(method: Method): Boolean = {
    method.name.contains("$mc") && method.name.endsWith("$sp")
  }

  private def short(name: String) = {
    name.substring(name.lastIndexOf('.') + 1)
  }

  private def isDefaultArg(m: Method): Boolean = {
    m.name.contains("$default$")
  }
}
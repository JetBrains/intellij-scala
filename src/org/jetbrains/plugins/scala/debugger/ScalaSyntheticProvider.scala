package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.engine.SyntheticTypeComponentProvider
import com.sun.jdi.{Field, Method, ReferenceType, TypeComponent}
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil

import scala.collection.JavaConverters._

/**
 * Nikolay.Tropin
 * 2014-12-03
 */
class ScalaSyntheticProvider extends SyntheticTypeComponentProvider {
  override def isSynthetic(typeComponent: TypeComponent): Boolean = {
    val isScala = DebuggerUtil.isScala(typeComponent.declaringType(), default = false)
    if (!isScala) return false

    typeComponent match {
      case m: Method if m.isConstructor && isAnonFun(m.declaringType()) => true
      case m: Method if m.name() == "apply" && hasSpecializationMethod(m.declaringType()) => true
      case m: Method if isDefaultArg(m) => true
      case f: Field if f.name().startsWith("bitmap$") => true
      case _ => false
    }
  }

  private def isAnonFun(refType: ReferenceType): Boolean = {
    short(refType.name).contains("$anonfun")
  }

  private def hasSpecializationMethod(refType: ReferenceType): Boolean = {
    refType.methods().asScala.exists(isSpecialization)
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
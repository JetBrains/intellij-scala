package org.jetbrains.plugins.scala.debugger.ui

import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl
import com.intellij.debugger.ui.tree.FieldDescriptor
import com.intellij.openapi.project.Project
import com.sun.jdi.{ClassType, ReferenceType}
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.debugger.ui.ScalaObscureFieldDescriptor.objectSuffix

import scala.collection.JavaConverters._

/**
 * @author Nikolay.Tropin
 */
class ScalaObscureFieldDescriptor(delegate: FieldDescriptor, project: Project)
  extends FieldDescriptorImpl(project, delegate.getObject, delegate.getField) {

  private val field = getField
  private val fieldName = field.name()
  private val typeName = field.declaringType().name()

  override def getName: String = {
    val defaultName = delegate.getName
    getField match {
      case _ if isScalaObject => s"[object] ${lastPart(defaultName)}"
      case _ if isLocalFromOuterField => fieldName.takeWhile(_ != '$')
      case _ if nameStartsWithFqn || isFieldFromTrait => lastPart(fieldName)
      case _ => defaultName
    }
  }

  private def isLocalFromOuterField: Boolean = {
    val typeName = field.declaringType().name()
    typeName.stripSuffix("$").contains("$") && fieldName.count(_ == '$') == 1
  }

  private def nameStartsWithFqn: Boolean = {
    val fqnWithDollars = typeName.replace('.', '$')
    fieldName.startsWith(fqnWithDollars)
  }

  private def isFieldFromTrait = {
    def hasMethodForField(ref: ReferenceType) = {
      ref.methodsByName(fieldName).asScala.exists(_.signature().startsWith("()"))
    }

    field.declaringType() match {
      case ct: ClassType =>
        val traits = ct.allInterfaces().asScala.filter(DebuggerUtil.isScala(_))
        traits.exists(hasMethodForField)
      case _ => false
    }
  }

  private def isScalaObject: Boolean = {
    fieldName.endsWith(objectSuffix)
  }

  private def lastPart(name: String) = {
    val stripped = name.stripSuffix(objectSuffix)
    stripped.drop(stripped.lastIndexOf('$') + 1)
  }
}

object ScalaObscureFieldDescriptor {
  private val objectSuffix = "$module"
}

package org.jetbrains.plugins.scala
package debugger.ui

import java.util

import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.ui.tree.ValueDescriptor
import com.intellij.debugger.ui.tree.render._
import com.intellij.util.StringBuilderSpinAllocator
import com.sun.jdi._
import org.jetbrains.plugins.scala.debugger.filters.ScalaDebuggerSettings

/**
 * User: Dmitry Naydanov
 * Date: 11/14/12
 */
class ObjectRefElementRenderer extends ClassRenderer {
  override def getUniqueId = "ObjectRefElementRenderer"
  override def getName = "Friendly ObjectRef"
  override def setName(name: String) { }
  override def isEnabled: Boolean = ScalaDebuggerSettings.getInstance().FRIENDLY_OBJECT_REF_DISPLAY_ENABLED
  override def setEnabled(enabled: Boolean) {/*see ScalaDebuggerSettingsConfigurable */}

  override def isApplicable(tpe: Type): Boolean = tpe match {
    case ref: ReferenceType => ref.name() == "scala.runtime.ObjectRef"
    case _ => false
  }

  override def buildChildren(value: Value, builder: ChildrenBuilder, context: EvaluationContext) {
    value match {
      case obj: ObjectReference => super.buildChildren(obj.getValue(obj.referenceType() fieldByName "elem"), builder, context)
      case _ => 
    }
  }

  override def calcLabel(descriptor: ValueDescriptor, context: EvaluationContext, listener: DescriptorLabelListener): String = {
    val stringBuilder = StringBuilderSpinAllocator.alloc()

    descriptor.getValue match {
      case obj: ObjectReference =>
        val myLabelElem = context.getDebugProcess.invokeMethod(context, obj,
          obj.referenceType() methodsByName "toString" get 0, new util.ArrayList[AnyRef]()) match {
          case str: StringReference => str.value()
          case _ => "?"
        }

        stringBuilder append s"ObjectRef: $myLabelElem"
      case _ => stringBuilder append "{...}"
    }

    val label = stringBuilder.toString
    StringBuilderSpinAllocator dispose stringBuilder
    label
  }
}

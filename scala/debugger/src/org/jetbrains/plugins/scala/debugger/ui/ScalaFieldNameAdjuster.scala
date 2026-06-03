package org.jetbrains.plugins.scala.debugger.ui

import com.intellij.debugger.ui.tree.{FieldDescriptor, NodeDescriptor, NodeDescriptorNameAdjuster}
import com.sun.jdi.{ClassType, ReferenceType}
import org.jetbrains.plugins.scala.debugger.ScalaSyntheticProvider
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.debugger.ui.ScalaFieldNameAdjuster.objectSuffix

import scala.jdk.CollectionConverters._

class ScalaFieldNameAdjuster extends NodeDescriptorNameAdjuster {

  override def isApplicable(descriptor: NodeDescriptor): Boolean = {
    descriptor match {
      case fd: FieldDescriptor if fd.getObject != null =>
        DebuggerUtil.isScala(fd.getObject.referenceType()) && isObscureName(fd.getField.name())
      case _ => false
    }
  }

  override def fixName(name: String, descriptor: NodeDescriptor): String = {
    descriptor match {
      case fd: FieldDescriptor =>
        val field = fd.getField
        val typeName = field.declaringType().name()

        def isLocalFromOuterField: Boolean = {
          val typeName = field.declaringType().name()
          typeName.stripSuffix("$").contains("$") && name.count(_ == '$') == 1
        }

        def nameStartsWithFqn: Boolean = {
          val fqnWithDollars = typeName.replace('.', '$')
          name.startsWith(fqnWithDollars)
        }

        def isFieldFromTrait = {
          def hasMethodForField(ref: ReferenceType) = {
            ref.methodsByName(name).asScala.exists(_.signature().startsWith("()"))
          }

          field.declaringType() match {
            case ct: ClassType =>
              val traits = ct.allInterfaces().asScala.filter(DebuggerUtil.isScala(_))
              traits.exists(hasMethodForField)
            case _ => false
          }
        }

        def isScalaObject: Boolean = {
          name.endsWith(objectSuffix)
        }

        def lastPart(name: String) = {
          val stripped = name.stripSuffix(objectSuffix)
          stripped.drop(stripped.lastIndexOf('$') + 1)
        }
        val unspecializedName = ScalaSyntheticProvider.unspecializedName(name)

        if (unspecializedName.nonEmpty)
          unspecializedName.get
        else if (isScalaObject)
          s"[object] ${lastPart(name)}"
        else if (isLocalFromOuterField)
          name.takeWhile(_ != '$')
        else if (nameStartsWithFqn || isFieldFromTrait)
          lastPart(name)
        else name.stripSuffix("$impl")
    }
  }

  private def isObscureName(s: String) = s != "$outer" && s.contains("$")
}

object ScalaFieldNameAdjuster {
  private val objectSuffix = "$module"
}

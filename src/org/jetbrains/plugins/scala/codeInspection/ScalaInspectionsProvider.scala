package org.jetbrains.plugins.scala
package codeInspection


import com.intellij.codeInspection.InspectionToolProvider
import com.intellij.openapi.components.ApplicationComponent
import fileNameInspection.FileNameInspection
import java.lang.String
import packageNameInspection.PackageNameInspection
import referenceInspections.CyclicReferencesInspection

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.06.2009
 */

class ScalaInspectionsProvider extends InspectionToolProvider with ApplicationComponent {
  def getInspectionClasses: Array[java.lang.Class[_]] = Array[java.lang.Class[_]](
    classOf[CyclicReferencesInspection],
    classOf[FileNameInspection],
    classOf[PackageNameInspection]
  )

  def initComponent: Unit = {}

  def disposeComponent: Unit = {}

  def getComponentName: String = "Scala Inspections Provider"
}
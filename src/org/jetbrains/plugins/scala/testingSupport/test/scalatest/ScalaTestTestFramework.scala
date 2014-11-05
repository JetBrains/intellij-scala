package org.jetbrains.plugins.scala
package testingSupport.test.scalatest

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestFramework

/**
 * @author Ksenia.Sautina
 * @since 5/15/12
 */

class ScalaTestTestFramework extends AbstractTestFramework {

  def getDefaultSuperClass: String = {
    val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext())
    val scalaProjectSettings = ScalaProjectSettings.getInstance(project)
    scalaProjectSettings.getScalaTestDefaultSuperClass
  }

  def getName: String = "ScalaTest"

  def getMarkerClassFQName: String = "org.scalatest.Suite"

  def getMnemonic: Char = 'c'
}

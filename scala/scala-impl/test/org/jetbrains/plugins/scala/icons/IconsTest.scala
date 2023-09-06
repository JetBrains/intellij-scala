package org.jetbrains.plugins.scala.icons

import com.intellij.testFramework.UsefulTestCase
import org.junit.Assert
import org.junit.Assert.assertTrue

import java.lang.reflect.Field

class IconsTest extends UsefulTestCase {

  override def isIconRequired: Boolean = true

  // Icons resolve is deferred, so if some icon references to some non-existing path it will not be detected on application startup
  def testAllIconsAreResolvable(): Unit = {
    val iconFields = getAllIconFields(classOf[Icons])

    //noinspection UnstableApiUsage,ApiStatus
    iconFields.sortBy(_.getName).foreach { f =>
      val icon = f.get(null)
      icon match {
        case cached: com.intellij.ui.icons.CachedImageIcon =>
          // contains com.intellij.openapi.util.IconLoader.EMPTY_ICON in case icon can't be loaded
          val realIcon0 = cached.getRealIcon
          // val realIcon1 = cached.doGetRealIcon()
          assertTrue(
            s"""Cant resolve icon ${f.getName} (${icon.getClass}) (${cached.getOriginalPath}).
               |realIcon0: $realIcon0
               |""".stripMargin.trim,
            !realIcon0.toString.toLowerCase().startsWith("empty icon ") // EMPTY_ICON is private
          )
        case other =>
          Assert.fail(s"Change the test to verify icon resolving for class '${other.getClass}'")
      }
    }
  }

  private def getAllIconFields(iconsHolder: Class[_]): Array[Field] = {
    val fields = iconsHolder.getDeclaredFields
    val iconFields = fields.filter(_.getType == classOf[javax.swing.Icon])
    assert(iconFields.nonEmpty)
    iconFields
  }
}
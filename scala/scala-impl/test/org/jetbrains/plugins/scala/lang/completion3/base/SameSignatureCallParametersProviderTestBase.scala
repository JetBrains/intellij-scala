package org.jetbrains.plugins.scala.lang.completion3.base

import com.intellij.codeInsight.completion.JavaCompletionUtil
import com.intellij.ui.LayeredIcon
import com.intellij.ui.icons.RowIcon
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase.{createPresentation, hasItemText, hasLookupString}

import javax.swing.Icon
import scala.jdk.CollectionConverters._

abstract class SameSignatureCallParametersProviderTestBase extends ScalaCompletionTestBase {


  protected def checkLookupElement(fileText: String,
                                   resultText: String,
                                   item: String,
                                   isSuper: Boolean,
                                   icons: Icon*): Unit =
    super.doRawCompletionTest(fileText, resultText) { lookup =>
      val icon = createPresentation(lookup).getIcon
      val iconsActual = allIcons(icon)

      val lookupStringMatches = hasLookupString(lookup, item)
      val isSuperMatches = lookup.getUserData(JavaCompletionUtil.SUPER_METHOD_PARAMETERS) == (if (isSuper) java.lang.Boolean.TRUE else null)
      val iconsMatch = iconsActual == icons
      lookupStringMatches && isSuperMatches && iconsMatch
    }

  protected def checkNoCompletion(fileText: String): Unit =
    super.checkNoCompletion(fileText) {
      _.getLookupString.contains(", ")
    }

  //noinspection SameParameterValue
  protected def checkNoCompletionWithoutTailText(fileText: String, lookupString: String): Unit =
    super.checkNoCompletion(fileText) {
      hasItemText(_, lookupString)(tailText = null)
    }

  protected def allIcons(icon: Icon): Seq[Icon] = icon match {
    case icon: LayeredIcon =>
      icon
        .getAllLayers
        .reverse
        .toSeq
        .flatMap {
          case layer: RowIcon => layer.getAllIcons.asScala
          case layer => Seq(layer)
        }
    case _ => Seq(icon)
  }

}

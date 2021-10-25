package scala.meta

import com.intellij.application.options.RegistryManager

package object intellij {

  // TODO: remove somewhere in 2022.1 / 2022.2 SCL-19637
  //  (also review and update all usage places, maybe remove some stale code)
  def isMetaAnnotationExpansionEnabled: Boolean =
    RegistryManager.getInstance().is("scala.meta.annotation.expansion.legacy.support")
}

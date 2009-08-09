package org.jetbrains.plugins.scala
package codeInsight.template.impl

import com.intellij.codeInsight.template.FileTypeBasedContextType

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.01.2009
 */

class ScalaLiveTemplateContextType extends FileTypeBasedContextType("SCALA", "Scala", ScalaFileType.SCALA_FILE_TYPE)
/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.lang.parser

import com.intellij.psi.impl.DebugUtil
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.NoSdkFileSetTestBase
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.{ScalaFeatures, ScalaLanguageLevel}

import java.nio.file.Path

/**
 * @see [[org.jetbrains.plugins.scala.lang.parser.scala3.Scala3ParserTest]]
 */
class ScalaParserTest extends NoSdkFileSetTestBase {
  override protected def relativeTestDataPath: Path = Path.of("parser", "data")

  override protected def transform(testName: String, fileText: String): String = {
    val version = new ScalaVersion(ScalaLanguageLevel.Scala_2_13, "8")
    val features = ScalaFeatures.forParserTests(version)
    val file = ScalaPsiElementFactory.createScalaFileFromText(fileText, features, shouldTrimText = false)(project)
    //noinspection ScalaWrongPlatformMethodsUsage
    DebugUtil.psiToString(file, true).replace(": " + file.getName, "")
  }
}

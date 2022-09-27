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

package org.jetbrains.plugins.scala.lang.parser;

import junit.framework.Test;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaVersion;
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase;
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel;

public class ScalaParserTest extends TestCase {
    @NotNull
    public static Test suite() {
        return new ScalaFileSetTestCase("/parser/data") {
            @Override
            protected boolean supportedInScalaVersion(ScalaVersion version) {
                return version.$less(new ScalaVersion(ScalaLanguageLevel.Scala_2_13, "9"));
            }
        };
    }
}

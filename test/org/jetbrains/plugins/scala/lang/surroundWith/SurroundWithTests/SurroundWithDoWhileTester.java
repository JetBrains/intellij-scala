/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.lang.surroundWith.SurroundWithTests;

import org.jetbrains.plugins.scala.lang.surroundWith.SurroundWithTester;
import org.jetbrains.plugins.scala.util.ScalaToolsFactory;
import junit.framework.Test;
import com.intellij.lang.surroundWith.Surrounder;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 24.01.2007
 */
public class SurroundWithDoWhileTester extends SurroundWithTester {
  public static Test suite() {
    return new SurroundWithDoWhileTester();
  }

  public SurroundWithDoWhileTester() {
    super(System.getProperty("path") != null ? System.getProperty("path") : "test/org/jetbrains/plugins/scala/lang/surroundWith/data/dowhile");
  }

  public Surrounder surrounder() {
    return ScalaToolsFactory.getInstance().createSurroundDescriptors().getSurroundDescriptors()[0].getSurrounders()[5];
  }
}
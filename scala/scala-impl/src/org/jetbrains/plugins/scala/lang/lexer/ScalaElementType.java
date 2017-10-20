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

package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.scala.ScalaLanguage;

/**
 * @author ilyas
 * Date: 24.09.2006
 *
 */
public class ScalaElementType extends IElementType {
  private String debugName = null;
  private boolean leftBound;

  public ScalaElementType(String debugName) {
    this(debugName, true);
  }

  public ScalaElementType(String debugName, boolean leftBound) {
      super(debugName, ScalaLanguage.INSTANCE);
    this.debugName = debugName;
    this.leftBound = leftBound;
  }

  public String toString() {
    return debugName;
  }

  @Override
  public boolean isLeftBound() {
    return leftBound;
  }
}

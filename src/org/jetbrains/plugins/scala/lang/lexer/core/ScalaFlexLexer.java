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

package org.jetbrains.plugins.scala.lang.lexer.core;

import com.intellij.lexer.FlexAdapter;

import java.io.Reader;

/**
 * @author ilyas
 * Date: 24.09.2006
 *
 */
public class ScalaFlexLexer extends FlexAdapter {
  public ScalaFlexLexer() {
    super(new _ScalaCoreLexer((Reader) null));
  }

  public _ScalaCoreLexer getScalaCoreLexer() {
    return (_ScalaCoreLexer) getFlex();
  }

  @Override
  public int getState() {
    final _ScalaCoreLexer scalaCoreLexer = getScalaCoreLexer();
    return super.getState() << 1 |
        (scalaCoreLexer.isInsideInterpolatedStringInjection() || scalaCoreLexer.isInterpolatedStringState() ? 1 : 0);
  }
}

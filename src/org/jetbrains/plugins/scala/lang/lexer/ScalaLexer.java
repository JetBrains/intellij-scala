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

import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.tree.TokenSet;

/**
 * Created by IntelliJ IDEA.
 * User: Ilya.Sergey
 * Date: 29.01.2007
 * Time: 18:29:28
 * To change this template use File | Settings | File Templates.
 */
public class ScalaLexer extends MergingLexerAdapter {

  public ScalaLexer() {
    super(new ScalaFlexLexer(),
      TokenSet.create(
        ScalaTokenTypes.tWHITE_SPACE_IN_LINE,
        ScalaTokenTypes.tCOMMENT_CONTENT
      ));
  }
}

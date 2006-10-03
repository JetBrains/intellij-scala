/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.lexer.FlexAdapter;

import java.io.Reader;

/**
 * Author: Ilya Sergey
 * Date: 24.09.2006
 * Time: 16:37:53
 */
public class ScalaLexer extends FlexAdapter {
    public ScalaLexer() {
        super(new _ScalaLexer((Reader) null));
    }
}

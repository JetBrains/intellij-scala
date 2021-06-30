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

package org.jetbrains.plugins.scala;

import com.intellij.lang.Language;
import com.intellij.lang.jvm.JvmLanguage;

/**
 * @author ilyas
 * Date: 20.09.2006
 */
public class ScalaLanguage extends Language implements JvmLanguage {

    public static final ScalaLanguage INSTANCE = new ScalaLanguage();

    private ScalaLanguage() {
        super("Scala");
    }
}
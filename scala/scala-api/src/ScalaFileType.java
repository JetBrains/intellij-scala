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

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.icons.Icons;

import javax.swing.*;
import java.util.Arrays;

final public class ScalaFileType extends LanguageFileTypeBase {

    public static final ScalaFileType INSTANCE = new ScalaFileType();

    private ScalaFileType() {
        super(ScalaLanguage.INSTANCE);
    }

    public String getExtensionWithDot() {
        return "." + getDefaultExtension();
    }

    @Override
    @NotNull
    public Icon getIcon() {
        return Icons.SCALA_FILE;
    }

    @Override
    public boolean isMyFileExtension(VirtualFile file) {
        return Arrays.asList("scala", "mill").contains(file.getExtension());
    }
}

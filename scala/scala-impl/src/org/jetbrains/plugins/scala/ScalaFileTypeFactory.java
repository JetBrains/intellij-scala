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

package org.jetbrains.plugins.scala;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author ilyas
 */
public class ScalaFileTypeFactory extends FileTypeFactory {
  public void createFileTypes(final @NotNull FileTypeConsumer consumer) {
    ScalaFileType fileType = ScalaFileType.INSTANCE;
    consumer.consume(fileType,
            fileType.getDefaultExtension() + FileTypeConsumer.EXTENSION_DELIMITER + ScalaFileType.WORKSHEET_EXTENSION);
  }
}
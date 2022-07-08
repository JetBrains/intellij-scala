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

package org.jetbrains.plugins.scala.highlighter;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.LayerDescriptor;
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter;
import com.intellij.openapi.fileTypes.EditorHighlighterProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaLanguage;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx;
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes;
import org.jetbrains.plugins.scalaDoc.ScalaDocLanguage;

import static com.intellij.openapi.fileTypes.SyntaxHighlighterFactory.getSyntaxHighlighter;

public final class ScalaEditorHighlighterProvider implements EditorHighlighterProvider {

    @Override
    public ScalaEditorHighlighter getEditorHighlighter(@Nullable Project project,
                                                       @NotNull FileType fileType,
                                                       @Nullable VirtualFile virtualFile,
                                                       @NotNull EditorColorsScheme colors) {
        return new ScalaEditorHighlighter(
                getSyntaxHighlighter(ScalaLanguage.INSTANCE, project, virtualFile),
                getSyntaxHighlighter(XMLLanguage.INSTANCE, project, virtualFile),
                getSyntaxHighlighter(ScalaDocLanguage.INSTANCE, project, virtualFile),
                colors
        );
    }

    // TODO something is wrong with the highlighter, see comment in SCL-18701
    private static final class ScalaEditorHighlighter extends LayeredLexerEditorHighlighter {

        private ScalaEditorHighlighter(@NotNull SyntaxHighlighter scalaHighlighter,
                                       @NotNull SyntaxHighlighter xmlHighlighter,
                                       @NotNull SyntaxHighlighter scalaDocHighlighter,
                                       @NotNull EditorColorsScheme colors) {
            super(scalaHighlighter, colors);

            registerLayer(
                    ScalaTokenTypesEx.SCALA_XML_CONTENT,
                    new LayerDescriptor(xmlHighlighter, "\n", HighlighterColors.TEXT)
            );

            registerLayer(
                    ScalaDocElementTypes.SCALA_DOC_COMMENT,
                    new LayerDescriptor(scalaDocHighlighter, "\n", DefaultHighlighter.DOC_COMMENT)
            );
        }

        // workaround for an apparent bug in IntelliJ platform which applies
        // the background attribute (e.g. Scaladoc Commnent) *after* the foreground attribute (e.g. Scaladoc Tag)
        //
        // See https://youtrack.jetbrains.net/issue/SCL-3122
        //
        // Either LayeredLexerEditorHighlighter#convertAttributes or LayeredLexerEditorHighlighter#Mapper#getAttributes
        // should be fixed instead.
//  @Override
//  protected TextAttributes convertAttributes(@NotNull TextAttributesKey[] keys) {
//    TextAttributesKey[] reversed = ArrayUtil.reverseArray(keys);
//    return super.convertAttributes(reversed);
//  }
    }
}

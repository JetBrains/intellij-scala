package org.jetbrains.plugins.hocon.codestyle;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;

public class HoconCustomCodeStyleSettings extends CustomCodeStyleSettings {
    public HoconCustomCodeStyleSettings(CodeStyleSettings container) {
        super("HoconCodeStyleSettings", container);
    }

    // SPACING
    // Around operators
    public boolean SPACE_BEFORE_COLON = false;
    public boolean SPACE_AFTER_COLON = true;
    public boolean SPACE_BEFORE_ASSIGNMENT = true;
    public boolean SPACE_AFTER_ASSIGNMENT = true;
    // Before left brace
    public boolean SPACE_BEFORE_LBRACE_AFTER_PATH = true;
    // Within
    public boolean SPACE_WITHIN_SUBSTITUTION_BRACES = false;
    // Other
    public boolean SPACE_AFTER_QMARK = false;

    //WRAPPING AND BRACES
    //Keep when reformatting
    public boolean HASH_COMMENTS_AT_FIRST_COLUMN = false;
    public boolean DOUBLE_SLASH_COMMENTS_AT_FIRST_COLUMN = false;

    public int OBJECTS_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    public boolean OBJECTS_ALIGN_WHEN_MULTILINE = false;
    public boolean OBJECTS_NEW_LINE_AFTER_LBRACE = true;
    public boolean OBJECTS_RBRACE_ON_NEXT_LINE = true;

    public int LISTS_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    public boolean LISTS_ALIGN_WHEN_MULTILINE = false;
    public boolean LISTS_NEW_LINE_AFTER_LBRACKET = false;
    public boolean LISTS_RBRACKET_ON_NEXT_LINE = false;

    public int OBJECT_FIELDS_WITH_COLON_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    public boolean OBJECT_FIELDS_COLON_ON_NEXT_LINE = false;

    public int OBJECT_FIELDS_WITH_ASSIGNMENT_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    public boolean OBJECT_FIELDS_ASSIGNMENT_ON_NEXT_LINE = false;

    public int INCLUDED_RESOURCE_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;

    //BLANK LINES
    public int KEEP_BLANK_LINES_IN_OBJECTS = 2;
    public int KEEP_BLANK_LINES_IN_LISTS = 2;
    public int KEEP_BLANK_LINES_BEFORE_RBRACE = 2;
    public int KEEP_BLANK_LINES_BEFORE_RBRACKET = 2;
}

package org.jetbrains.jps.incremental.scala.local.worksheet.repl_interface;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PrintWriterReporter implements ILoopWrapperReporter {

    public static final String IJReportPrefix = "###IJ_REPORT###";

    // severity, line, column, lineContentEncoded, messageEncoded
    private static final Pattern IJReportRegexp = Pattern.compile("(\\w+):(\\d+):(\\d+):(.*?):(.*)");

    private final PrintWriter printWriter;

    public PrintWriterReporter(PrintWriter printWriter) {
        this.printWriter = printWriter;
    }

    @Override
    public void report(String severity,
                       Integer line,
                       Integer column,
                       String lineContent,
                       String message) {
        String reportLine = String.format(
                "%s%s:%d:%d:%s:%s\n",
                IJReportPrefix, severity, line, column, encode(lineContent), encode(message)
        );
        printWriter.print(reportLine);
        printWriter.flush();
    }


    public static String encode(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        byte[] encodedBytes = Base64.getEncoder().encode(bytes);
        return new String(encodedBytes, StandardCharsets.UTF_8);
    }

    public static String decode(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        byte[] decodedBytes = Base64.getDecoder().decode(bytes);
        return new String(decodedBytes, StandardCharsets.UTF_8);
    }

    public static class MessageLineParsed {
        public final String severity;
        public final Integer line;
        public final Integer column;
        public final String lineContent;
        public final String message;

        public MessageLineParsed(String severity, Integer line, Integer column, String lineContent, String message) {
            this.severity = severity;
            this.line = line;
            this.column = column;
            this.lineContent = lineContent;
            this.message = message;
        }
    }

    public static MessageLineParsed parse(String messageLine) {
        Matcher matcher = IJReportRegexp.matcher(messageLine);
        MessageLineParsed result = null;
        if (matcher.matches()) {
            try {
                String severity = matcher.group(1);
                int line = Integer.parseInt(matcher.group(2));
                int column = Integer.parseInt(matcher.group(3));
                String lineContent = decode(matcher.group(4));
                String message = decode(matcher.group(5));
                result = new MessageLineParsed(severity, line, column, lineContent, message);
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }
}

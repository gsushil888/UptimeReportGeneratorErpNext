package com.sushil.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import java.io.InputStream;

public class TimestampUtils {

    // The ISO format used for parsing incoming timestamp strings.
    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    // Retrieve PDF timestamp format from configuration.
    private static final String REPORT_TIMESTAMP_FORMAT = ConfigLoader.get("pdf.date.format");

    /**
     * Parses a date string (which may contain relative terms like "now") and returns a formatted date string.
     * This version is used for display in the PDF.
     *
     * @param dateInput the date string to parse.
     * @return a formatted date string.
     */
    public static String parseDate(String dateInput) {
        if (dateInput.contains("now")) {
            LocalDate today = LocalDate.now();
            if (dateInput.contains("-")) {
                String[] parts = dateInput.split("-");
                int daysToSubtract = Integer.parseInt(parts[1].replace("d/d", ""));
                return formatDate(today.minusDays(daysToSubtract)
                        .atStartOfDay()
                        .minusHours(5)
                        .minusMinutes(30)
                        .format(ISO_FORMAT));
            } else if (dateInput.equals("now/d")) {
                return formatDate(today.atStartOfDay()
                        .minusHours(5)
                        .minusMinutes(30)
                        .format(ISO_FORMAT));
            }
        } else {
            return formatDate(dateInput);
        }
        return "";
    }

    /**
     * Converts a timestamp string into a display-friendly format.
     *
     * @param dateString the timestamp string in ISO format.
     * @return the formatted date string.
     */
    public static String formatDate(String dateString) {
        Instant instant = Instant.parse(dateString);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a")
                .withZone(ZoneId.of("Asia/Kolkata"));
        return formatter.format(instant);
    }

    /**
     * Parses a date string for use in PDF file names.
     *
     * @param dateInput the date string to parse.
     * @return the formatted date string for PDF file names.
     */
    public static String parseDateForPdfPath(String dateInput) {
        if (dateInput.contains("now")) {
            LocalDate today = LocalDate.now();
            if (dateInput.contains("-")) {
                String[] parts = dateInput.split("-");
                int daysToSubtract = Integer.parseInt(parts[1].replace("d/d", ""));
                return formatDateForPdfPath(today.minusDays(daysToSubtract)
                        .atStartOfDay()
                        .minusHours(5)
                        .minusMinutes(30)
                        .format(ISO_FORMAT));
            } else if (dateInput.equals("now/d")) {
                return formatDateForPdfPath(today.atStartOfDay()
                        .minusHours(5)
                        .minusMinutes(30)
                        .format(ISO_FORMAT));
            }
        } else {
            return formatDateForPdfPath(dateInput);
        }
        return "";
    }

    /**
     * Converts a timestamp string into a format suitable for PDF file names.
     *
     * @param dateString the timestamp string in ISO format.
     * @return the formatted date string for PDF file names.
     */
    public static String formatDateForPdfPath(String dateString) {
        Instant instant = Instant.parse(dateString);

        DateTimeFormatter defaultFormatter = DateTimeFormatter.ofPattern("EEE_dd-MMM-yyyy hh:mm a")
                .withZone(ZoneId.of("Asia/Kolkata"));

        String pdfDateFormat = REPORT_TIMESTAMP_FORMAT;
        DateTimeFormatter formatter;
        if (pdfDateFormat != null && !pdfDateFormat.trim().isEmpty()) {
            try {
                formatter = DateTimeFormatter.ofPattern(pdfDateFormat).withZone(ZoneId.of("Asia/Kolkata"));
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid pdf.date.format in configuration. Using default format.");
                formatter = defaultFormatter;
            }
        } else {
            System.err.println("pdf.date.format not provided. Using default format.");
            formatter = defaultFormatter;
        }

        return formatter.format(instant);
    }

    /**
     * Reads the report dates from a JSON stream and returns them in a map.
     *
     * @param queryJsonStream the InputStream containing the JSON.
     * @return a map with keys "from" and "to" containing formatted date strings.
     */
    public static Map<String, String> getReportDates(InputStream queryJsonStream) {
        Map<String, String> reportDates = new HashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(queryJsonStream);
            JsonNode rangeNode = rootNode.path("query").path("bool").path("filter").get(0)
                    .path("range").path("@timestamp");
            String reportFromTimestamp = rangeNode.path("gte").asText();
            String reportToTimestamp = rangeNode.path("lt").asText();

            reportDates.put("from", parseDateForPdfPath(reportFromTimestamp));
            reportDates.put("to", parseDateForPdfPath(reportToTimestamp));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return reportDates;
    }
}


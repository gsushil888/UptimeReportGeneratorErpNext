package com.sushil.elasticsearch.report_generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.sushil.elasticsearch.config.ConfigLoader;
import com.sushil.elasticsearch.util.PdfReportUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import jakarta.json.stream.JsonParser;

public class AppUptimeReportGenerator {

	public static String REPORT_FROM = "";
	public static String REPORT_TO = "";
	private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
	private static final String REPORT_TIMESTAMP_FORMAT = ConfigLoader.get("pdf.date.format");

	// --------------General Main Methods-------------------------------------

	public static void addUptimeSection(Document document, ElasticsearchClient client,
			Map<String, String> jsonFilePathMap) throws DocumentException, IOException {
		// (1) Add the time interval (from the "interval" JSON)
		try (InputStream intervalStream = new FileInputStream(jsonFilePathMap.get("interval"))) {
			addTimeIntervalInPdfFromIntervalJson(intervalStream, document);
		}

		// (2) Add overall average uptime data
		try (InputStream overallStream = new FileInputStream(jsonFilePathMap.get("overall"))) {
			generateAverageUptimeTable(client, document, overallStream);
		}

		// (3) Add detailed records (timestamps when uptime dropped below 100%)
		try (InputStream intervalStream = new FileInputStream(jsonFilePathMap.get("interval"))) {
			generateAllRecordsIntervalTable(client, document, intervalStream);
		}
	}

	// -----Method to add time interval in json-----------

	public static void addTimeIntervalInPdfFromIntervalJson(InputStream queryJsonStream, Document document) {
		try {

			ObjectMapper mapper = new ObjectMapper();
			JsonNode rootNode = mapper.readTree(queryJsonStream);
			JsonNode rangeNode = rootNode.path("query").path("bool").path("filter").get(0).path("range")
					.path("@timestamp");
			String reportFromTimestamp = rangeNode.path("gte").asText();
			String reportToTimestamp = rangeNode.path("lt").asText();
			String timeZoneTimestamp = rangeNode.path("time_zone").asText();

			try {
				if (!reportFromTimestamp.equals("now-1d/d")) {
					REPORT_FROM = convertDateInAbsoluteFromRelative(reportFromTimestamp);
				}
				if (!reportToTimestamp.equals("now-1d/d")) {
					REPORT_TO = convertDateInAbsoluteFromRelative(reportFromTimestamp);
				}
				String parsedJsonFromDate = convertDateInAbsoluteFromRelative(reportFromTimestamp);
				String parsedJsonToDate = convertDateInAbsoluteFromRelative(reportToTimestamp);
				PdfReportUtils.addStyledTimeIntervalSectionHeader(document,
						"From: " + convertDateInAbsoluteFromRelative(reportFromTimestamp) + " |  To: "
								+ convertDateInAbsoluteFromRelative(reportToTimestamp));
			} catch (DocumentException e) {
				e.printStackTrace();
			}
		} catch (IOException e) {
			System.err.println("Json gte and lt is not a day format");
			e.printStackTrace();
		}
	}

	private static String convertDateInAbsoluteFromRelative(String dateInput) {
		if (dateInput.contains("now")) {
			LocalDateTime now = LocalDateTime.now();
			if (dateInput.contains("-")) {
				String[] parts = dateInput.split("-");
				if (parts[1].endsWith("d/d")) {
					int daysToSubtract = Integer.parseInt(parts[1].replace("d/d", ""));
					REPORT_FROM = formatDateInReadableFormat(now.minusDays(daysToSubtract).toLocalDate().atStartOfDay()
							.minusHours(5).minusMinutes(30).format(ISO_FORMAT));
					return formatDateInReadableFormat(now.minusDays(daysToSubtract).toLocalDate().atStartOfDay()
							.minusHours(5).minusMinutes(30).format(ISO_FORMAT));
				} else if (parts[1].endsWith("h/h")) {
					int hoursToSubtract = Integer.parseInt(parts[1].replace("h/h", ""));
					REPORT_FROM = formatDateInReadableFormat(now.minusHours(hoursToSubtract).withMinute(0).withSecond(0)
							.withNano(0).minusHours(5).minusMinutes(30).format(ISO_FORMAT));
					return formatDateInReadableFormat(now.minusHours(hoursToSubtract).withMinute(0).withSecond(0)
							.withNano(0).minusHours(5).minusMinutes(30).format(ISO_FORMAT));
				}
			} else if (dateInput.equals("now/d")) {
				REPORT_TO = formatDateInReadableFormat(
						now.toLocalDate().atStartOfDay().minusHours(5).minusMinutes(30).format(ISO_FORMAT));
				return formatDateInReadableFormat(
						now.toLocalDate().atStartOfDay().minusHours(5).minusMinutes(30).format(ISO_FORMAT));
			} else if (dateInput.equals("now/h")) {
				REPORT_TO = formatDateInReadableFormat(
						now.withMinute(0).withSecond(0).withNano(0).minusHours(5).minusMinutes(30).format(ISO_FORMAT));
				return formatDateInReadableFormat(
						now.withMinute(0).withSecond(0).withNano(0).minusHours(5).minusMinutes(30).format(ISO_FORMAT));
			}
		} else {
			return formatDateInReadableFormat(dateInput);
		}
		return "";
	}

	public static String formatDateInReadableFormat(String dateString) {
		Instant instant = Instant.parse(dateString);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a")
				.withZone(ZoneId.of("Asia/Kolkata"));
		return formatter.format(instant);
	}

	// --------------------Add tables in pdf while extracting data----------------

	private static void generateAverageUptimeTable(ElasticsearchClient client, Document document,
			InputStream queryStream) throws IOException, DocumentException {
		JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
		JsonParser jsonParser = jsonpMapper.jsonProvider().createParser(queryStream);

		SearchRequest searchRequest = SearchRequest.of(b -> b.index("uptime_index").withJson(jsonParser, jsonpMapper));
		SearchResponse<Map> searchResponse = client.search(searchRequest, Map.class);
		Map<String, Aggregate> aggregate = searchResponse.aggregations();
		Aggregate groupByUrlAggregation = aggregate.get("group_by_url");
		List<StringTermsBucket> buckets = groupByUrlAggregation.sterms().buckets().array();
//		System.out.println(buckets);

		// Add Section Header
		PdfReportUtils.addStyledSectionHeader(document, "Section A: APPLICATION URL UPTIME");
		PdfReportUtils.addStyledSubtitleSectionHeader(document,
				"1: Overall average URL uptime percentage for the specified report time range");

		// Create Table with Better Formatting
		PdfPTable table = new PdfPTable(new float[] { 3, 2 });
		table.setWidthPercentage(100);
		table.setSpacingBefore(5f);
		table.setSpacingAfter(5f);

		table.addCell(PdfReportUtils.createHeaderCell("URL"));
		table.addCell(PdfReportUtils.createHeaderCell("Average Uptime"));

		if (buckets.isEmpty()) {
			PdfPCell noDataCell = new PdfPCell(
					new Phrase("No Data Available", FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.BLACK)));
			noDataCell.setColspan(2);
			noDataCell.setHorizontalAlignment(Element.ALIGN_CENTER);
			noDataCell.setPadding(5);
			table.addCell(noDataCell);
		} else {
			for (StringTermsBucket bucket : buckets) {
				String url = bucket.key().stringValue();
				Double avgUptime = bucket.aggregations().get("avg_uptime").avg().value();

				table.addCell(new Paragraph(url));

				PdfPCell uptimeCell = new PdfPCell(new Paragraph(String.format("%.2f%%", avgUptime)));
				uptimeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
				uptimeCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

				if (avgUptime < 100) {
					uptimeCell.setBackgroundColor(BaseColor.CYAN);
				}

				table.addCell(uptimeCell);
			}
		}

		document.add(table);
	}

	private static void generateAllRecordsIntervalTable(ElasticsearchClient client, Document document,
			InputStream queryJsonFile) throws IOException, DocumentException {
		JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
		JsonParser jsonParser = jsonpMapper.jsonProvider().createParser(queryJsonFile);

		SearchRequest searchRequest = SearchRequest.of(b -> b.index("uptime_index").withJson(jsonParser, jsonpMapper));
		SearchResponse<Map> searchResponse = client.search(searchRequest, Map.class);

		Map<String, Aggregate> aggregate = searchResponse.aggregations();
		Aggregate groupByUrlAggregation = aggregate.get("group_by_url");
		List<StringTermsBucket> buckets = groupByUrlAggregation.sterms().buckets().array();

		PdfReportUtils.addStyledSubtitleSectionHeader(document,
				"2:Timestamp when application URL uptime dropped below 100%");

		if (buckets.isEmpty()) {
			PdfPTable table = PdfReportUtils.createTableWithUrlHeader("No Data Available");
			PdfReportUtils.addNoDowntimeRow(table);
			document.add(table);
		} else {
			for (StringTermsBucket bucket : buckets) {
				String url = bucket.key().stringValue();
				Aggregate avgUptimeAggregations = bucket.aggregations().get("hourly_avg");
				List<DateHistogramBucket> avgUptimeBuckets = avgUptimeAggregations.dateHistogram().buckets().array();

				List<DateHistogramBucket> lessThan100Buckets = new ArrayList<>();
				for (DateHistogramBucket datebucket : avgUptimeBuckets) {
					double uptimeValue = datebucket.aggregations().get("avg_uptime").avg().value();
					if (uptimeValue < 100.0) {
						lessThan100Buckets.add(datebucket);
					}
				}

				PdfPTable table = PdfReportUtils.createTableWithUrlHeader(url);
				if (lessThan100Buckets.isEmpty()) {
					PdfReportUtils.addNoDowntimeRow(table);
				} else {
					PdfReportUtils.populateTableWithData(table, lessThan100Buckets);
				}
				document.add(table);
			}
		}

		PdfReportUtils.addStyledSubtitleSectionHeader(document,
				"3: Detailed records of application URL uptime percentage for the specified report time range");
		if (buckets.isEmpty()) {
			PdfPTable table = PdfReportUtils.createTableWithUrlHeader("No Data Available");
			PdfReportUtils.addNoDowntimeRow(table);
			document.add(table);
		} else {
			for (StringTermsBucket bucket : buckets) {
				String url = bucket.key().stringValue();
				Aggregate avgUptimeAggregations = bucket.aggregations().get("hourly_avg");
				List<DateHistogramBucket> allBuckets = avgUptimeAggregations.dateHistogram().buckets().array();

				PdfPTable table = PdfReportUtils.createTableWithUrlHeader(url);
				PdfReportUtils.populateTableWithData(table, allBuckets);
				document.add(table);
			}
		}
	}

	// ----------------------OLD--------------------

	public static String parseDateForPdfPath(String dateInput) {
		if (dateInput.contains("now")) {
			LocalDate today = LocalDate.now();
			if (dateInput.contains("-")) {
				String[] parts = dateInput.split("-");
				int daysToSubtract = Integer.parseInt(parts[1].replace("d/d", ""));
				REPORT_FROM = formatDateForPdfPath(today.minusDays(daysToSubtract).atStartOfDay().minusHours(5)
						.minusMinutes(30).format(ISO_FORMAT));
				return formatDateForPdfPath(today.minusDays(daysToSubtract).atStartOfDay().minusHours(5)
						.minusMinutes(30).format(ISO_FORMAT));
			} else if (dateInput.equals("now/d")) {
				REPORT_TO = formatDateForPdfPath(
						today.minusDays(0).atStartOfDay().minusHours(5).minusMinutes(30).format(ISO_FORMAT));
				return formatDateForPdfPath(
						today.minusDays(0).atStartOfDay().minusHours(5).minusMinutes(30).format(ISO_FORMAT));
			}
		} else {
			return formatDateForPdfPath(dateInput);
		}
		return "";
	}

	public static Map<String, String> addReportDatesToPdfPath(InputStream queryJsonStream) {
		Map<String, String> reportDates = new HashMap<>();
		try {

			ObjectMapper mapper = new ObjectMapper();
			JsonNode rootNode = mapper.readTree(queryJsonStream);

			JsonNode rangeNode = rootNode.path("query").path("bool").path("filter").get(0).path("range")
					.path("@timestamp");
			String reportFromTimestamp = rangeNode.path("gte").asText();
			String reportToTimestamp = rangeNode.path("lt").asText();

			reportDates.put("from", parseDateForPdfPath(reportFromTimestamp));
			reportDates.put("to", parseDateForPdfPath(reportToTimestamp));

		} catch (IOException e) {
			e.printStackTrace();
		}
		return reportDates;
	}

	public static String formatDateForPdfPath(String dateString) {
		Instant instant = Instant.parse(dateString);

		DateTimeFormatter defaultFormatter = DateTimeFormatter.ofPattern("EEE_dd-MMM-yyyy hh:mm a")
				.withZone(ZoneId.of("Asia/Kolkata"));

		String pdfDateFormat = ConfigLoader.get("pdf.date.format");
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

}

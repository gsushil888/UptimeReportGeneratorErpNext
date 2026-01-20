package com.sushil.elasticsearch;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import jakarta.json.stream.JsonParser;

public class LoadNUptimeReportGenerator {

	private static final String ENVIRONMENT_PREFIX = ConfigLoader.get("exclude.hostname.start_with");
	private static final String METRICBEAT_INDEX = "metricbeat_index";
	private static final String SECTION_HEADER_LOAD_USAGE = "Section D: CPU LOAD AVERAGE AND SYSTEM UPTIME";

	private static String SUBTITLE_HEADER_CURRENT_LOAD_DETAILS = "1: Current Load Details";
	private static final List<String> CURRENT_LOAD_TABLE_HEADERS = Arrays.asList("IP Address", "Load 1m", "Load 5m",
			"Load 15m");

	private static String SUBTITLE_HEADER_AVERAGE_LOAD_DETAILS = "2: Average Load Details for specified report time range";
	private static final List<String> AVERAGE_LOAD_TABLE_HEADERS = Arrays.asList("IP Address", "Load 1m", "Load 5m",
			"Load 15m");

	private static String SUBTITLE_HEADER_CURRENT_UPTIME_DETAILS = "3: Current Uptime Details";
	private static final List<String> CURRENT_UPTIME_TABLE_HEADERS = Arrays.asList("IP Address", "Uptime");

	private static List<StringTermsBucket> buckets = null;
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm:ss a");
	private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

	// --------Main--------------
	public static void addLoadSection(ElasticsearchClient client, Document document,
			Map<String, String> jsonFilePathMap) throws IOException, DocumentException {
		try (InputStream dataStream = new FileInputStream(jsonFilePathMap.get("load"))) {
			document.newPage();
			PdfReportUtils.addStyledSectionHeader(document, SECTION_HEADER_LOAD_USAGE);
			Aggregate hostAgg = queryHostNameAggFromElasticsearch(client, dataStream);
			List<StringTermsBucket> buckets = hostAgg.sterms().buckets().array();
			generateCurrentLoadDetails(document, buckets);
			generateAverageLoadDetails(document, buckets);
			generateCurrentUptimeDetails(document, buckets);
		} catch (Exception e) {
			System.err.println(e);
		}
	}

	// ---------Load Averages----------
	private static void generateCurrentLoadDetails(Document document, List<StringTermsBucket> buckets)
			throws DocumentException {

		PdfPTable table = createLoadNUptimeTable(CURRENT_LOAD_TABLE_HEADERS, new float[] { 2, 1, 1, 1 });

		int initialRowCount = table.getRows().size();
		boolean hasData = false;

		for (StringTermsBucket bucket : buckets) {

			Aggregate loadAgg = bucket.aggregations().get("load_metrics");
			Aggregate currentLoadAgg = loadAgg.filter().aggregations().get("current_loads");

			List<Map<String, Object>> loadRecords = extractRecords(currentLoadAgg);

			for (Map<String, Object> record : loadRecords) {

				// ✅ ENVIRONMENT FILTER
				Object envObj = record.get("environment");
				if (envObj != null && envObj.toString().startsWith(ENVIRONMENT_PREFIX)) {
					continue;
				}

				String hostname = record.containsKey("ip_address") ? record.get("ip_address").toString()
						: bucket.key().stringValue();

				String load1 = "-", load5 = "-", load15 = "-";

				Map<String, Object> system = (Map<String, Object>) record.get("system");
				if (system != null) {
					Map<String, Object> load = (Map<String, Object>) system.get("load");
					if (load != null) {
						load1 = String.valueOf(load.getOrDefault("1", "-"));
						load5 = String.valueOf(load.getOrDefault("5", "-"));
						load15 = String.valueOf(load.getOrDefault("15", "-"));
					}
				}

				table.addCell(PdfReportUtils.createDataCell(hostname));
				table.addCell(PdfReportUtils.createDataCell(load1));
				table.addCell(PdfReportUtils.createDataCell(load5));
				table.addCell(PdfReportUtils.createDataCell(load15));

				hasData = true;
			}
		}

		// ✅ NO DATA WHEN ALL RECORDS EXCLUDED
		if (!hasData) {
			PdfPCell noData = new PdfPCell(
					new Phrase("No Data Available", FontFactory.getFont(FontFactory.HELVETICA, 10)));
			noData.setColspan(CURRENT_LOAD_TABLE_HEADERS.size());
			noData.setHorizontalAlignment(Element.ALIGN_CENTER);
			noData.setPadding(5);
			table.addCell(noData);
		}

		String subtitle = SUBTITLE_HEADER_CURRENT_LOAD_DETAILS + " (" + DateTimeFormatter
				.ofPattern("dd-MM-yyyy hh:mm a").withZone(ZoneId.of("Asia/Kolkata")).format(Instant.now()) + ")";

		PdfReportUtils.addStyledSubtitleSectionHeader(document, subtitle);
		document.add(table);
	}

	private static void generateAverageLoadDetails(Document document, List<StringTermsBucket> buckets)
			throws DocumentException {

		PdfPTable table = createLoadNUptimeTable(AVERAGE_LOAD_TABLE_HEADERS, new float[] { 2, 1, 1, 1 });

		boolean hasData = false;

		for (StringTermsBucket bucket : buckets) {

			Aggregate loadAgg = bucket.aggregations().get("load_metrics");
			Aggregate sampleAgg = loadAgg.filter().aggregations().get("current_loads");

			List<Map<String, Object>> records = extractRecords(sampleAgg);
			if (records.isEmpty()) {
				continue;
			}

			// ✅ ENVIRONMENT FILTER (from first record)
			Object envObj = records.get(0).get("environment");
			if (envObj != null && envObj.toString().startsWith(ENVIRONMENT_PREFIX)) {
				continue;
			}

			String hostname = records.get(0).containsKey("ip_address") ? records.get(0).get("ip_address").toString()
					: bucket.key().stringValue();

			double load1 = loadAgg.filter().aggregations().get("load_1_avg").avg().value();
			double load5 = loadAgg.filter().aggregations().get("load_5_avg").avg().value();
			double load15 = loadAgg.filter().aggregations().get("load_15_avg").avg().value();

			table.addCell(PdfReportUtils.createDataCell(hostname));
			table.addCell(PdfReportUtils.createDataCell(String.format("%.2f", load1)));
			table.addCell(PdfReportUtils.createDataCell(String.format("%.2f", load5)));
			table.addCell(PdfReportUtils.createDataCell(String.format("%.2f", load15)));

			hasData = true;
		}

		if (!hasData) {
			PdfPCell noData = new PdfPCell(
					new Phrase("No Data Available", FontFactory.getFont(FontFactory.HELVETICA, 10)));
			noData.setColspan(AVERAGE_LOAD_TABLE_HEADERS.size());
			noData.setHorizontalAlignment(Element.ALIGN_CENTER);
			noData.setPadding(5);
			table.addCell(noData);
		}

		PdfReportUtils.addStyledSubtitleSectionHeader(document, SUBTITLE_HEADER_AVERAGE_LOAD_DETAILS);

		document.add(table);
	}

	// -----------UPTIME-------------
	private static void generateCurrentUptimeDetails(Document document, List<StringTermsBucket> buckets)
			throws DocumentException {

		PdfPTable table = createLoadNUptimeTable(CURRENT_UPTIME_TABLE_HEADERS, new float[] { 1, 1 });

		boolean hasData = false;

		for (StringTermsBucket bucket : buckets) {

			Aggregate uptimeAgg = bucket.aggregations().get("uptime_metrics");
			Aggregate currentUptimeAgg = uptimeAgg.filter().aggregations().get("current_uptime");

			List<Map<String, Object>> uptimeRecords = extractRecords(currentUptimeAgg);

			for (Map<String, Object> record : uptimeRecords) {

				// ✅ ENVIRONMENT FILTER
				Object envObj = record.get("environment");
				if (envObj != null && envObj.toString().startsWith(ENVIRONMENT_PREFIX)) {
					continue;
				}

				String hostname = record.containsKey("ip_address") ? record.get("ip_address").toString()
						: bucket.key().stringValue();

				String uptimeValue = "-";

				Map<String, Object> system = (Map<String, Object>) record.get("system");
				if (system != null) {
					Map<String, Object> uptime = (Map<String, Object>) system.get("uptime");
					if (uptime != null) {
						Map<String, Object> duration = (Map<String, Object>) uptime.get("duration");
						if (duration != null && duration.get("ms") != null) {
							uptimeValue = convertMillisToReadableFormat(Long.parseLong(duration.get("ms").toString()));
						}
					}
				}

				table.addCell(PdfReportUtils.createDataCell(hostname));
				table.addCell(PdfReportUtils.createDataCell(uptimeValue));

				hasData = true;
			}
		}

		if (!hasData) {
			PdfPCell noData = new PdfPCell(
					new Phrase("No Data Available", FontFactory.getFont(FontFactory.HELVETICA, 10)));
			noData.setColspan(CURRENT_UPTIME_TABLE_HEADERS.size());
			noData.setHorizontalAlignment(Element.ALIGN_CENTER);
			noData.setPadding(5);
			table.addCell(noData);
		}

		String subtitle = SUBTITLE_HEADER_CURRENT_UPTIME_DETAILS + " (" + DateTimeFormatter
				.ofPattern("dd-MM-yyyy hh:mm a").withZone(ZoneId.of("Asia/Kolkata")).format(Instant.now()) + ")";

		PdfReportUtils.addStyledSubtitleSectionHeader(document, subtitle);
		document.add(table);
	}

	// ------------COMMON ------------------
	public static Aggregate queryHostNameAggFromElasticsearch(ElasticsearchClient client, InputStream queryStream)
			throws IOException {
		JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
		JsonParser jsonParser = jsonpMapper.jsonProvider().createParser(queryStream);
		SearchRequest searchRequest = SearchRequest
				.of(b -> b.index(METRICBEAT_INDEX).withJson(jsonParser, jsonpMapper));
		SearchResponse<Map> searchResponse = client.search(searchRequest, Map.class);
		return searchResponse.aggregations().get("group_by_hostname");
	}

	private static String convertMillisToReadableFormat(long millis) {
		long seconds = millis / 1000;
		long days = seconds / 86400;
		seconds %= 86400;
		long hours = seconds / 3600;
		seconds %= 3600;
		long minutes = seconds / 60;
		seconds %= 60;

		StringBuilder readableFormat = new StringBuilder();
		if (days > 0)
			readableFormat.append(days).append("d ");
		if (hours > 0)
			readableFormat.append(hours).append("h ");
		if (minutes > 0)
			readableFormat.append(minutes).append("m ");
		if (seconds > 0)
			readableFormat.append(seconds).append("s ");

		return readableFormat.toString().trim();
	}

	public static PdfPTable createLoadNUptimeTable(List<String> headers, float[] columnWidths)
			throws DocumentException {
		PdfPTable table = new PdfPTable(headers.size());
		table.setWidthPercentage(100);
		table.setSpacingBefore(5f);
		table.setSpacingAfter(5f);
		table.setWidths(columnWidths);

		for (String header : headers) {
			table.addCell(PdfReportUtils.createHeaderCell(header));
		}
		return table;
	}

	@SuppressWarnings("unchecked")
	public static List<Map<String, Object>> extractRecords(Aggregate aggregate) {
		return aggregate.topHits().hits().hits().stream().map(hit -> (Map<String, Object>) hit.source().to(Map.class))
				.collect(Collectors.toList());
	}

}

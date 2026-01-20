package com.sushil.elasticsearch.report_generator;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.sushil.elasticsearch.config.ConfigLoader;
import com.sushil.elasticsearch.dao.all.ReportDao;
import com.sushil.elasticsearch.model.cpu.CpuDetailRecord;
import com.sushil.elasticsearch.model.cpu.CpuThresholdRecord;
import com.sushil.elasticsearch.model.cpu.CpuUsageRecord;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

public class CpuReportGenerator {

	// ❌ REMOVED global ENVIRONMENT_PREFIX

	private static final String SECTION_HEADER_CPU_USAGE = "Section B: CPU USAGE";

	private static String SUBTITLE_HEADER_CURRENT_CPU_DETAILS =
			"1: Current CPU Usage Details";

	private static String SUBTITLE_HEADER_CPU_EXCEEDS =
			"2. Time range where the CPU usage exceeded the 20% threshold continuously for over 5 minutes.";

	private static String SUBTITLE_HEADER_CPU_ALL_DETAILS =
			"3. Timestamp when CPU usage exceeded the 20% threshold";

	private static final List<String> CURRENT_CPU_HEADERS = List.of(
			"IP Address", "System (%)", "User (%)", "IO Wait (%)",
			"Steal (%)", "Nice (%)", "Total (%)", "Idle (%)");

	private static final List<String> THRESHOLD_HEADERS = List.of(
			"IP Address", "Start At", "End At", "Avg (%)", "Max (%)");

	private static final List<String> DETAIL_HEADERS = List.of(
			"Timestamp", "User (%)", "System (%)", "Total (%)");

	private static final DateTimeFormatter FORMATTER =
			DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm:ss a")
					.withZone(ZoneId.of("Asia/Kolkata"));

	private CpuReportGenerator() {
	}

	// ================== PUBLIC ENTRY ==================
	// ✅ entity parameter added
	public static void addCpuSection(
			ElasticsearchClient client,
			Document document,
			Map<String, String> jsonFilePathMap,
			String entity) throws Exception {

		// ✅ entity-wise exclusion prefix
		String excludedEnvPrefix = getExcludedEnvPrefix(entity);

		ReportDao dao = new ReportDao(client);

		byte[] currentCpuBytes =
				Files.readAllBytes(Paths.get(jsonFilePathMap.get("current_cpu")));
		byte[] cpuBytes =
				Files.readAllBytes(Paths.get(jsonFilePathMap.get("cpu")));

		document.newPage();
		addStyledSectionHeader(document, SECTION_HEADER_CPU_USAGE);

		double cpuThreshold;
		try (InputStream cpuStream = new ByteArrayInputStream(cpuBytes)) {
			cpuThreshold = detectCpuThreshold(cpuStream);
		}

		List<CpuUsageRecord> currentRecords;
		try (InputStream currentCpuStream = new ByteArrayInputStream(currentCpuBytes)) {
			currentRecords = dao.fetchCurrentCpuRecords(currentCpuStream);
		}
		if (currentRecords == null)
			currentRecords = Collections.emptyList();

		addCurrentCpuTable(document, currentRecords, cpuThreshold, excludedEnvPrefix);

		List<CpuThresholdRecord> thresholdRecords;
		try (InputStream cpuStream = new ByteArrayInputStream(cpuBytes)) {
			thresholdRecords = dao.fetchCpuThresholdExceedRecords(cpuStream, cpuThreshold);
		}
		if (thresholdRecords == null)
			thresholdRecords = Collections.emptyList();

		addThresholdTable(document, thresholdRecords, cpuThreshold, excludedEnvPrefix);

		Map<String, List<CpuDetailRecord>> detailRecords;
		try (InputStream cpuStream = new ByteArrayInputStream(cpuBytes)) {
			detailRecords = dao.fetchCpuDetailRecords(cpuStream);
		}
		if (detailRecords == null)
			detailRecords = Collections.emptyMap();

		addDetailTable(document, detailRecords, cpuThreshold, excludedEnvPrefix);
	}

	// ================== ENTITY-WISE EXCLUSION ==================
	private static String getExcludedEnvPrefix(String entity) {
		return ConfigLoader.get("exclude.hostname.start_with." + entity.toUpperCase());
	}

	private static boolean shouldExclude(String environment, String excludedEnvPrefix) {
		return excludedEnvPrefix != null
				&& !excludedEnvPrefix.isBlank()
				&& environment != null
				&& environment.startsWith(excludedEnvPrefix);
	}

	// ================== CURRENT CPU ==================
	private static void addCurrentCpuTable(
			Document document,
			List<CpuUsageRecord> records,
			double threshold,
			String excludedEnvPrefix) throws DocumentException {

		addStyledSubtitleSectionHeader(document,
				withTimestamp(SUBTITLE_HEADER_CURRENT_CPU_DETAILS));

		PdfPTable table =
				createTableWithHeaders(CURRENT_CPU_HEADERS,
						new float[]{2, 1, 1, 1, 1, 1, 1, 1});

		boolean hasData = false;

		for (CpuUsageRecord rec : records) {

			if (rec == null) continue;

			if (shouldExclude(rec.getEnvironment(), excludedEnvPrefix)) continue;

			table.addCell(createDataCell(rec.gethostname() != null ? rec.gethostname() : "N/A"));
			table.addCell(createHighlightedCell(rec.getSystemPct(), threshold));
			table.addCell(createHighlightedCell(rec.getUserPct(), threshold));
			table.addCell(createHighlightedCell(rec.getIoWaitPct(), threshold));
			table.addCell(createHighlightedCell(rec.getStealPct(), threshold));
			table.addCell(createHighlightedCell(rec.getNicePct(), threshold));
			table.addCell(createHighlightedCell(rec.getTotalPct(), threshold));
			table.addCell(createHighlightedCell(rec.getIdlePct(), threshold));

			hasData = true;
		}

		if (!hasData) {
			addNoDataRow(table, CURRENT_CPU_HEADERS.size(), "No Data Available");
		}

		document.add(table);
	}

	// ================== THRESHOLD ==================
	private static void addThresholdTable(
			Document document,
			List<CpuThresholdRecord> records,
			double threshold,
			String excludedEnvPrefix) throws DocumentException {

		addStyledSubtitleSectionHeader(document, SUBTITLE_HEADER_CPU_EXCEEDS);

		PdfPTable table =
				createTableWithHeaders(THRESHOLD_HEADERS,
						new float[]{2, 2, 2, 1, 1});

		boolean hasData = false;

		for (CpuThresholdRecord rec : records) {

			if (shouldExclude(rec.getEnvironment(), excludedEnvPrefix)) continue;

			table.addCell(createDataCell(rec.gethostname()));
			table.addCell(createDataCell(FORMATTER.format(rec.getStartTime())));
			table.addCell(createDataCell(FORMATTER.format(rec.getEndTime())));
			table.addCell(createHighlightedCell(rec.getAvgPct(), threshold));
			table.addCell(createHighlightedCell(rec.getMaxPct(), threshold));

			hasData = true;
		}

		if (!hasData) {
			addNoDataRow(table, THRESHOLD_HEADERS.size(), "No threshold exceeded");
		}

		document.add(table);
	}

	// ================== DETAIL ==================
	private static void addDetailTable(
			Document document,
			Map<String, List<CpuDetailRecord>> detailRecords,
			double threshold,
			String excludedEnvPrefix) throws DocumentException {

		addStyledSubtitleSectionHeader(document, SUBTITLE_HEADER_CPU_ALL_DETAILS);

		PdfPTable table =
				createTableWithHeaders(DETAIL_HEADERS,
						new float[]{3, 2, 2, 2});

		boolean hasData = false;

		for (Map.Entry<String, List<CpuDetailRecord>> entry : detailRecords.entrySet()) {

			List<CpuDetailRecord> records = entry.getValue();
			if (records == null || records.isEmpty()) continue;

			if (shouldExclude(records.get(0).getEnvironment(), excludedEnvPrefix)) continue;

			PdfPCell ipCell = new PdfPCell(
					new Phrase("IP Address: " + entry.getKey(),
							FontFactory.getFont(FontFactory.HELVETICA_BOLD)));
			ipCell.setColspan(DETAIL_HEADERS.size());
			ipCell.setBackgroundColor(BaseColor.YELLOW);
			ipCell.setHorizontalAlignment(Element.ALIGN_CENTER);
			table.addCell(ipCell);

			for (CpuDetailRecord rec : records) {
				table.addCell(createDataCell(FORMATTER.format(rec.getTimestamp())));
				table.addCell(createHighlightedCell(rec.getUserPct(), threshold));
				table.addCell(createHighlightedCell(rec.getSystemPct(), threshold));
				table.addCell(createHighlightedCell(rec.getTotalPct(), threshold));
			}

			hasData = true;
		}

		if (!hasData) {
			addNoDataRow(table, DETAIL_HEADERS.size(), "No detailed data available");
		}

		document.add(table);
	}

	// ================== THRESHOLD DETECTION ==================
	private static double detectCpuThreshold(InputStream cpuStream) throws Exception {

		ObjectMapper mapper = new ObjectMapper();
		JsonNode rootNode = mapper.readTree(cpuStream);

		double userCpu = 0.0;
		double systemCpu = 0.0;

		for (JsonNode condition : rootNode.at("/query/bool/should")) {
			JsonNode range = condition.path("range");
			userCpu = Math.max(userCpu,
					range.at("/system.cpu.user.norm.pct/gte").asDouble(0.0));
			systemCpu = Math.max(systemCpu,
					range.at("/system.cpu.system.norm.pct/gte").asDouble(0.0));
		}

		double threshold = Math.max(userCpu, systemCpu) * 100;

		SUBTITLE_HEADER_CPU_EXCEEDS =
				SUBTITLE_HEADER_CPU_EXCEEDS.replace("20%",
						String.format("%.0f%%", threshold));

		SUBTITLE_HEADER_CPU_ALL_DETAILS =
				SUBTITLE_HEADER_CPU_ALL_DETAILS.replace("20%",
						String.format("%.0f%%", threshold));

		return threshold;
	}

	// ================== PDF HELPERS ==================
	private static PdfPCell createHighlightedCell(double value, double threshold) {
		PdfPCell cell =
				new PdfPCell(new Paragraph(
						String.format("%.2f", value),
						FontFactory.getFont(FontFactory.HELVETICA, 11)));
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setPadding(4f);
		if (value >= threshold)
			cell.setBackgroundColor(BaseColor.CYAN);
		return cell;
	}

	private static void addStyledSectionHeader(Document document, String title)
			throws DocumentException {

		Chunk sectionChunk =
				new Chunk(title,
						FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13));
		sectionChunk.setLocalDestination(title);

		Paragraph sectionHeader = new Paragraph();
		sectionHeader.setSpacingBefore(3f);
		sectionHeader.setSpacingAfter(1f);
		sectionHeader.setAlignment(Paragraph.ALIGN_LEFT);
		sectionHeader.add(sectionChunk);

		document.add(sectionHeader);
	}

	private static void addStyledSubtitleSectionHeader(Document document, String title)
			throws DocumentException {

		Paragraph p =
				new Paragraph(title,
						FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11));
		p.setSpacingBefore(3f);
		p.setSpacingAfter(2f);
		p.setAlignment(Paragraph.ALIGN_LEFT);

		document.add(p);
	}

	private static PdfPTable createTableWithHeaders(List<String> headers, float[] widths)
			throws DocumentException {

		PdfPTable table = new PdfPTable(headers.size());
		table.setWidths(widths);
		table.setWidthPercentage(100);
		table.setSpacingBefore(5f);
		table.setSpacingAfter(5f);

		for (String h : headers) {
			PdfPCell c =
					new PdfPCell(new Paragraph(h,
							FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
			c.setBackgroundColor(BaseColor.LIGHT_GRAY);
			c.setHorizontalAlignment(Element.ALIGN_CENTER);
			c.setPadding(5f);
			table.addCell(c);
		}
		return table;
	}

	private static PdfPCell createDataCell(String text) {
		PdfPCell c =
				new PdfPCell(new Paragraph(text,
						FontFactory.getFont(FontFactory.HELVETICA, 11)));
		c.setHorizontalAlignment(Element.ALIGN_CENTER);
		c.setPadding(4f);
		return c;
	}

	private static void addNoDataRow(PdfPTable table, int cols, String msg) {
		PdfPCell c =
				new PdfPCell(new Paragraph(msg,
						FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 11)));
		c.setColspan(cols);
		c.setHorizontalAlignment(Element.ALIGN_CENTER);
		c.setPadding(5f);
		table.addCell(c);
	}

	private static String withTimestamp(String subtitle) {
		DateTimeFormatter f =
				DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a");
		return subtitle + " (" + LocalDateTime.now().format(f) + ")";
	}
}

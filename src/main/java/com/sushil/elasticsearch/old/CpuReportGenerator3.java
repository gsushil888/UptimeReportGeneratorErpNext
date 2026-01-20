package com.sushil.elasticsearch.old;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
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
import com.itextpdf.text.Font;
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

public class CpuReportGenerator3 {

	private static final String ENVIRONMENT_PREFIX = ConfigLoader.get("exclude.hostname.start_with");
	private static final String METRICBEAT_INDEX = "metricbeat_index";

	private static final String SECTION_HEADER_CPU_USAGE = "Section B: CPU USAGE";
	private static String SUBTITLE_HEADER_CURRENT_CPU_DETAILS = "1: Current CPU Usage Details";
	private static String SUBTITLE_HEADER_CPU_EXCEEDS = "2.Time range where the CPU usage exceeded the 20% threshold continuously for over 5 minutes.";
	private static String SUBTITLE_HEADER_CPU_ALL_DETAILS = "3.Timestamp when CPU usage exceeded the 20% threshold";

	private static final List<String> CURRENT_CPU_HEADERS = List.of("IP Address", "System (%)", "User (%)",
			"IO Wait (%)", "Steal (%)", "Nice (%)", "Total (%)", "Idle (%)");
	private static final List<String> THRESHOLD_HEADERS = List.of("IP Address", "Start At", "End At", "Avg (%)",
			"Max (%)");
	private static final List<String> DETAIL_HEADERS = List.of("Timestamp", "User (%)", "System (%)", "Total (%)");

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm:ss a")
			.withZone(ZoneId.of("Asia/Kolkata"));

	private CpuReportGenerator3() {
	}

	public static void addCpuSection(ElasticsearchClient client, Document document, Map<String, String> jsonFilePathMap)
			throws Exception {

		if (client == null) {
			throw new IllegalArgumentException("ElasticsearchClient cannot be null");
		}

		if (document == null) {
			throw new IllegalArgumentException("Document cannot be null");
		}

		if (!jsonFilePathMap.containsKey("current_cpu") || !jsonFilePathMap.containsKey("cpu")) {
			throw new IllegalArgumentException("JSON file paths missing in map");
		}

		ReportDao dao = new ReportDao(client);

		byte[] currentCpuBytes = Files.readAllBytes(Paths.get(jsonFilePathMap.get("current_cpu")));
		byte[] cpuBytes = Files.readAllBytes(Paths.get(jsonFilePathMap.get("cpu")));

		document.newPage();
		addStyledSectionHeader(document, SECTION_HEADER_CPU_USAGE);

		// Dynamic threshold detection
		double cpuThreshold;
		try (InputStream cpuStreamForThreshold = new ByteArrayInputStream(cpuBytes)) {
			cpuThreshold = detectCpuThreshold(cpuStreamForThreshold);
		}

		// 1. Current CPU
		List<CpuUsageRecord> currentRecords;
		try (InputStream currentCpuStream = new ByteArrayInputStream(currentCpuBytes)) {
			currentRecords = dao.fetchCurrentCpuRecords(currentCpuStream);
		}
		if (currentRecords == null)
			currentRecords = Collections.emptyList();
		addCurrentCpuTable(document, currentRecords, cpuThreshold);

		// 2. Threshold exceeded windows
		List<CpuThresholdRecord> thresholdRecords;
		try (InputStream cpuStreamForThresholdRecords = new ByteArrayInputStream(cpuBytes)) {
			thresholdRecords = dao.fetchCpuThresholdExceedRecords(cpuStreamForThresholdRecords, cpuThreshold);
		}
		if (thresholdRecords == null)
			thresholdRecords = Collections.emptyList();
		addThresholdTable(document, thresholdRecords, cpuThreshold);

		// 3. Detailed records
		Map<String, List<CpuDetailRecord>> detailRecords;
		try (InputStream cpuStreamForDetail = new ByteArrayInputStream(cpuBytes)) {
			detailRecords = dao.fetchCpuDetailRecords(cpuStreamForDetail);
		}
		if (detailRecords == null)
			detailRecords = Collections.emptyMap();
		addDetailTable(document, detailRecords, cpuThreshold);
	}

	private static double detectCpuThreshold(InputStream cpuStream) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode rootNode = mapper.readTree(cpuStream);

		double userCpuThreshold = 0.0;
		double systemCpuThreshold = 0.0;

		JsonNode shouldArray = rootNode.at("/query/bool/should");
		if (shouldArray.isArray()) {
			for (JsonNode condition : shouldArray) {
				JsonNode rangeNode = condition.path("range");

				userCpuThreshold = Math.max(userCpuThreshold,
						rangeNode.at("/system.cpu.user.norm.pct/gte").asDouble(0.0));
				systemCpuThreshold = Math.max(systemCpuThreshold,
						rangeNode.at("/system.cpu.system.norm.pct/gte").asDouble(0.0));
			}
		}
		double maxThreshold = Math.max(userCpuThreshold, systemCpuThreshold) * 100;
		SUBTITLE_HEADER_CPU_EXCEEDS = SUBTITLE_HEADER_CPU_EXCEEDS.replace("20%", String.format("%.0f%%", maxThreshold));
		SUBTITLE_HEADER_CPU_ALL_DETAILS = SUBTITLE_HEADER_CPU_ALL_DETAILS.replace("20%",
				String.format("%.0f%%", maxThreshold));
		return maxThreshold;
	}

	// ======== Current CPU Table ========
	private static void addCurrentCpuTable(Document document, List<CpuUsageRecord> records, double threshold)
			throws DocumentException {
		PdfPTable table = createTableWithHeaders(CURRENT_CPU_HEADERS, new float[] { 2, 1, 1, 1, 1, 1, 1, 1 });
		if (records.isEmpty()) {
			addNoDataRow(table, CURRENT_CPU_HEADERS.size(), "No Data Available");
		} else {
			for (CpuUsageRecord rec : records) {
				if (rec.gethostname().startsWith(ENVIRONMENT_PREFIX))
					continue;

				table.addCell(createDataCell(rec.gethostname()));
				table.addCell(createHighlightedCell(rec.getSystemPct(), threshold));
				table.addCell(createHighlightedCell(rec.getUserPct(), threshold));
				table.addCell(createHighlightedCell(rec.getIoWaitPct(), threshold));
				table.addCell(createHighlightedCell(rec.getStealPct(), threshold));
				table.addCell(createHighlightedCell(rec.getNicePct(), threshold));
				table.addCell(createHighlightedCell(rec.getTotalPct(), threshold));
				table.addCell(createHighlightedCell(rec.getIdlePct(), threshold));
			}
		}
		addStyledSubtitleSectionHeader(document, SUBTITLE_HEADER_CURRENT_CPU_DETAILS);
		document.add(table);
	}

	// ======== Threshold Table ========
	private static void addThresholdTable(Document document, List<CpuThresholdRecord> records, double threshold)
			throws DocumentException {
		PdfPTable table = createTableWithHeaders(THRESHOLD_HEADERS, new float[] { 2, 2, 2, 1, 1 });
		if (records.isEmpty()) {
			addNoDataRow(table, THRESHOLD_HEADERS.size(), "No threshold exceeded");
		} else {
			for (CpuThresholdRecord rec : records) {
				if (rec.gethostname().startsWith(ENVIRONMENT_PREFIX))
					continue;

				table.addCell(createDataCell(rec.gethostname()));
				table.addCell(createDataCell(FORMATTER.format(rec.getStartTime())));
				table.addCell(createDataCell(FORMATTER.format(rec.getEndTime())));
				table.addCell(createHighlightedCell(rec.getAvgPct(), threshold));
				table.addCell(createHighlightedCell(rec.getMaxPct(), threshold));
			}
		}
		addStyledSubtitleSectionHeader(document, SUBTITLE_HEADER_CPU_EXCEEDS);
		document.add(table);
	}

	// ======== Detail Table ========
	private static void addDetailTable(Document document, Map<String, List<CpuDetailRecord>> detailRecords,
			double threshold) throws DocumentException {
		PdfPTable table = createTableWithHeaders(DETAIL_HEADERS, new float[] { 3, 2, 2, 2 });
		if (detailRecords.isEmpty()) {
			addNoDataRow(table, DETAIL_HEADERS.size(), "No detailed data available");
		} else {
			for (Map.Entry<String, List<CpuDetailRecord>> entry : detailRecords.entrySet()) {
				String hostname = entry.getKey();
				if (hostname.startsWith(ENVIRONMENT_PREFIX))
					continue;

				PdfPCell hostCell = new PdfPCell(
						new Phrase("IP Address: " + hostname, FontFactory.getFont(FontFactory.HELVETICA_BOLD)));
				hostCell.setColspan(DETAIL_HEADERS.size());
				hostCell.setBackgroundColor(BaseColor.YELLOW);
				hostCell.setHorizontalAlignment(Element.ALIGN_CENTER);
				table.addCell(hostCell);

				for (CpuDetailRecord rec : entry.getValue()) {
					table.addCell(createDataCell(FORMATTER.format(rec.getTimestamp())));
					table.addCell(createHighlightedCell(rec.getUserPct(), threshold));
					table.addCell(createHighlightedCell(rec.getSystemPct(), threshold));
					table.addCell(createHighlightedCell(rec.getTotalPct(), threshold));
				}
			}
		}
		addStyledSubtitleSectionHeader(document, SUBTITLE_HEADER_CPU_ALL_DETAILS);
		document.add(table);
	}

	// ======== PDF Helpers ========
	// Highlighted cell based on threshold
	public static PdfPCell createHighlightedCell(double value, double threshold) {
		PdfPCell cell = new PdfPCell(
				new Paragraph(String.format("%.2f", value), FontFactory.getFont(FontFactory.HELVETICA, 11)));
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setPadding(4f);
		if (value >= threshold) {
			cell.setBackgroundColor(BaseColor.CYAN);
		}
		return cell;
	}

	// Styled section header
	public static void addStyledSectionHeader(Document document, String text) throws DocumentException {
		Chunk sectionChunk = new Chunk(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, BaseColor.BLACK));
		sectionChunk.setLocalDestination(text);
		Paragraph sectionHeader = new Paragraph();
		sectionHeader.setSpacingBefore(3f);
		sectionHeader.setSpacingAfter(1f);
		sectionHeader.setAlignment(Paragraph.ALIGN_LEFT);
		sectionHeader.add(sectionChunk);
		document.add(sectionHeader);
	}

	// Styled subtitle header
	public static void addStyledSubtitleSectionHeader(Document document, String text) throws DocumentException {
		Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BaseColor.BLACK);
		Paragraph sectionHeader = new Paragraph(text, sectionFont);
		sectionHeader.setSpacingBefore(3f);
		sectionHeader.setSpacingAfter(2f);
		sectionHeader.setAlignment(Paragraph.ALIGN_LEFT);
		document.add(sectionHeader);
	}

	// Table with headers
	public static PdfPTable createTableWithHeaders(List<String> headers, float[] columnWidths)
			throws DocumentException {
		PdfPTable table = new PdfPTable(headers.size());
		table.setWidths(columnWidths);
		table.setWidthPercentage(100);
		table.setSpacingBefore(5f);
		table.setSpacingAfter(5f);

		for (String header : headers) {
			PdfPCell cell = new PdfPCell(new Paragraph(header, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
			cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
			cell.setHorizontalAlignment(Element.ALIGN_CENTER);
			cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
			cell.setPadding(5f);
			table.addCell(cell);
		}
		return table;
	}

	// Regular data cell
	public static PdfPCell createDataCell(String text) {
		PdfPCell cell = new PdfPCell(new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA, 11)));
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setPadding(4f);
		return cell;
	}

	// Row for no data scenario
	public static void addNoDataRow(PdfPTable table, int colCount, String message) {
		PdfPCell cell = new PdfPCell(
				new Paragraph(message, FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 11, BaseColor.BLACK)));
		cell.setColspan(colCount);
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setPadding(5f);
		table.addCell(cell);
	}
}
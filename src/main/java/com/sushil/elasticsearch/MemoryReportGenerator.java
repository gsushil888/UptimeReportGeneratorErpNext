package com.sushil.elasticsearch;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import com.sushil.elasticsearch.dao.ReportDao;
import com.sushil.elasticsearch.model.memory.MemoryDetailRecord;
import com.sushil.elasticsearch.model.memory.MemoryThresholdRecord;
import com.sushil.elasticsearch.model.memory.MemoryUsageRecord;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

public class MemoryReportGenerator {

	private static final String ENVIRONMENT_PREFIX = ConfigLoader.get("exclude.hostname.start_with");

	private static final String SECTION_HEADER_MEMORY_USAGE = "Section C: MEMORY USAGE";

	private static String SUBTITLE_HEADER_CURRENT_MEMORY_DETAILS = "1: Current MEMORY Usage Details";

	private static String SUBTITLE_HEADER_MEMORY_EXCEEDS = "2. Time range where the MEMORY usage exceeded the 20% threshold continuously for over 5 minutes.";

	private static String SUBTITLE_HEADER_MEMORY_ALL_DETAILS = "3. Timestamp when MEMORY usage exceeded the 20% threshold";

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm:ss a")
			.withZone(ZoneId.of("Asia/Kolkata"));

	private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");

	private MemoryReportGenerator() {
	}

	// ================== PUBLIC ENTRY ==================
	public static void addMemorySection(ElasticsearchClient client, Document document,
			Map<String, String> jsonFilePathMap) throws Exception {

		ReportDao dao = new ReportDao(client);

		byte[] currentMemoryBytes = Files.readAllBytes(Paths.get(jsonFilePathMap.get("current_memory")));

		byte[] memoryBytes = Files.readAllBytes(Paths.get(jsonFilePathMap.get("memory")));

		document.newPage();
		addStyledSectionHeader(document, SECTION_HEADER_MEMORY_USAGE);

		double memoryThreshold;
		try (InputStream thresholdStream = new ByteArrayInputStream(memoryBytes)) {
			memoryThreshold = detectMemoryThreshold(thresholdStream);
		}

		List<MemoryUsageRecord> usageRecords;
		try (InputStream currentStream = new ByteArrayInputStream(currentMemoryBytes)) {
			usageRecords = dao.fetchCurrentMemoryRecords(currentStream);
		}
		addCurrentMemoryTable(document, usageRecords);

		List<MemoryThresholdRecord> thresholdRecords;
		try (InputStream thresholdStream = new ByteArrayInputStream(memoryBytes)) {
			thresholdRecords = dao.fetchMemoryThresholdRecords(thresholdStream, memoryThreshold);
		}
		addThresholdTable(document, thresholdRecords);

		Map<String, List<MemoryDetailRecord>> detailRecords;
		try (InputStream detailStream = new ByteArrayInputStream(memoryBytes)) {
			detailRecords = dao.fetchMemoryDetailRecords(detailStream);
		}
		addDetailTable(document, detailRecords);
	}

	// ========= THRESHOLD DETECTION FROM JSON AND UPDATE IT IN
	private static double detectMemoryThreshold(InputStream jsonStream) throws Exception {

		ObjectMapper mapper = new ObjectMapper();
		JsonNode rootNode = mapper.readTree(jsonStream);

		double threshold = 0.0;
		for (JsonNode condition : rootNode.at("/query/bool/should")) {
			threshold = Math.max(threshold, condition.at("/range/system.memory.actual.used.pct/gte").asDouble(0.0));
		}

		double thresholdPct = threshold * 100;

		SUBTITLE_HEADER_MEMORY_EXCEEDS = SUBTITLE_HEADER_MEMORY_EXCEEDS.replace("20%",
				DECIMAL_FORMAT.format(thresholdPct) + "%");

		SUBTITLE_HEADER_MEMORY_ALL_DETAILS = SUBTITLE_HEADER_MEMORY_ALL_DETAILS.replace("20%",
				DECIMAL_FORMAT.format(thresholdPct) + "%");

		return thresholdPct;
	}

	// ================== CURRENT MEMORY ==================
	private static void addCurrentMemoryTable(Document document, List<MemoryUsageRecord> records)
			throws DocumentException {

		addStyledSubtitleSectionHeader(document, withTimestamp(SUBTITLE_HEADER_CURRENT_MEMORY_DETAILS));

		PdfPTable table = new PdfPTable(2);
		table.setWidthPercentage(100);
		table.setSpacingBefore(5f);
		table.setSpacingAfter(5f);
		table.setWidths(new float[] { 3f, 2f });

		table.addCell(createMemorySpanHeaderCell("IP Address", 1));
		table.addCell(createMemorySpanHeaderCell("Used (%)", 1));

		boolean hasData = false;

		if (records != null) {
			for (MemoryUsageRecord rec : records) {

				if (rec == null) {
					continue;
				}

				String environment = rec.getEnvironment();
				if (environment != null && environment.startsWith(ENVIRONMENT_PREFIX)) {
					continue;
				}

				String hostname = rec.getHostname() != null ? rec.getHostname() : "N/A";

				double usedPct = 0.0;
				try {
					if (rec.getMemActualUsedPct() != null) {
						usedPct = Double.parseDouble(rec.getMemActualUsedPct());
					}
				} catch (NumberFormatException ignored) {
					usedPct = 0.0;
				}

				table.addCell(createMemoryDataCell(hostname));
				table.addCell(createHighlightedDataCell(usedPct));

				hasData = true;
			}
		}

		if (!hasData) {
			table.addCell(createNoDataCell("No memory usage data available", 2));
		}

		document.add(table);
	}

	// ================== THRESHOLD TABLE ==================
	private static void addThresholdTable(Document document, List<MemoryThresholdRecord> records)
			throws DocumentException {

		addStyledSubtitleSectionHeader(document, SUBTITLE_HEADER_MEMORY_EXCEEDS);

		PdfPTable table = new PdfPTable(5);
		table.setWidthPercentage(100);
		table.setSpacingBefore(5f);
		table.setSpacingAfter(5f);
		table.setWidths(new float[] { 2f, 3f, 3f, 2.5f, 2.5f });

		table.addCell(createMemoryHeaderCell("IP Address"));
		table.addCell(createMemoryHeaderCell("Start At"));
		table.addCell(createMemoryHeaderCell("End At"));
		table.addCell(createMemoryHeaderCell("Avg (%)"));
		table.addCell(createMemoryHeaderCell("Max (%)"));

		if (records.isEmpty()) {
			table.addCell(createNoDataCell("No servers reached the threshold", 5));
		} else {
			for (MemoryThresholdRecord rec : records) {

				String environment = rec.getEnvironment();
				if (environment != null && environment.startsWith(ENVIRONMENT_PREFIX)) {
					continue;
				}

				table.addCell(createMemoryDataCell(rec.getHostname()));
				table.addCell(createMemoryDataCell(rec.getStartAt()));
				table.addCell(createMemoryDataCell(rec.getEndAt()));
				table.addCell(createHighlightedDataCell(rec.getAvgPct()));
				table.addCell(createHighlightedDataCell(rec.getMaxPct()));
			}
		}
		document.add(table);
	}

	// ================== DETAIL TABLE ==================
	private static void addDetailTable(Document document, Map<String, List<MemoryDetailRecord>> recordMap)
			throws DocumentException {

		addStyledSubtitleSectionHeader(document, SUBTITLE_HEADER_MEMORY_ALL_DETAILS);

		if (recordMap.isEmpty()) {
			PdfPTable emptyTable = new PdfPTable(3);
			emptyTable.setWidthPercentage(100);
			emptyTable.setSpacingBefore(5f);
			emptyTable.setWidths(new float[] { 3f, 2.5f, 2.5f });

			emptyTable.addCell(createMemoryHeaderCell("Timestamp"));
			emptyTable.addCell(createMemoryHeaderCell("Swap Memory %"));
			emptyTable.addCell(createMemoryHeaderCell("Actual Memory %"));

			emptyTable.addCell(createNoDataCell("No detailed memory records available", 3));

			document.add(emptyTable);
			return;
		}

		for (Map.Entry<String, List<MemoryDetailRecord>> entry : recordMap.entrySet()) {

			List<MemoryDetailRecord> records = entry.getValue();
			if (records.isEmpty())
				continue;

			String environment = records.get(0).getEnvironment();
			if (environment != null && environment.startsWith(ENVIRONMENT_PREFIX)) {
				continue;
			}

			Paragraph hostPara = new Paragraph("Hostname: " + entry.getKey(),
					FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11));
			hostPara.setSpacingBefore(10);
			document.add(hostPara);

			PdfPTable table = new PdfPTable(3);
			table.setWidthPercentage(100);
			table.setSpacingBefore(5f);
			table.setWidths(new float[] { 3f, 2.5f, 2.5f });

			table.addCell(createMemoryHeaderCell("Timestamp"));
			table.addCell(createMemoryHeaderCell("Swap Memory %"));
			table.addCell(createMemoryHeaderCell("Actual Memory %"));

			for (MemoryDetailRecord rec : records) {
				table.addCell(createMemoryDataCell(rec.getTimestamp()));
				table.addCell(createHighlightedDataCell(rec.getSwapMemoryPct()));
				table.addCell(createHighlightedDataCell(rec.getActualMemoryPct()));
			}
			document.add(table);
		}
	}

	// ================== PDF HELPERS ==================
	private static PdfPCell createMemoryHeaderCell(String text) {
		PdfPCell cell = new PdfPCell(new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
		cell.setPadding(5f);
		return cell;
	}

	private static PdfPCell createMemorySpanHeaderCell(String text, int colSpan) {
		PdfPCell cell = new PdfPCell(new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
		cell.setColspan(colSpan);
		cell.setPadding(5f);
		return cell;
	}

	private static PdfPCell createMemoryDataCell(String text) {
		PdfPCell cell = new PdfPCell(new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA, 9)));
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setPadding(4f);
		return cell;
	}

	private static PdfPCell createHighlightedDataCell(double value) {
		PdfPCell cell = new PdfPCell(
				new Paragraph(DECIMAL_FORMAT.format(value), FontFactory.getFont(FontFactory.HELVETICA, 9)));
		cell.setPadding(5f);
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		if (value > 80) {
			cell.setBackgroundColor(BaseColor.CYAN);
		}
		return cell;
	}

	private static PdfPCell createNoDataCell(String message, int colspan) {
		PdfPCell cell = new PdfPCell(new Phrase(message, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
		cell.setColspan(colspan);
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setPadding(6f);
		return cell;
	}

	private static void addStyledSectionHeader(Document document, String title) throws DocumentException {

		Chunk sectionChunk = new Chunk(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13));
		sectionChunk.setLocalDestination(title);

		Paragraph sectionHeader = new Paragraph();
		sectionHeader.setSpacingBefore(3f);
		sectionHeader.setSpacingAfter(1f);
		sectionHeader.setAlignment(Paragraph.ALIGN_LEFT);
		sectionHeader.add(sectionChunk);

		document.add(sectionHeader);
	}

	private static void addStyledSubtitleSectionHeader(Document document, String title) throws DocumentException {

		Paragraph sectionHeader = new Paragraph(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11));
		sectionHeader.setSpacingBefore(3f);
		sectionHeader.setSpacingAfter(2f);
		sectionHeader.setAlignment(Paragraph.ALIGN_LEFT);

		document.add(sectionHeader);
	}

	/* ===== Timestamp Helper ===== */
	private static String withTimestamp(String subtitle) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a");
		return subtitle + " (" + LocalDateTime.now().format(formatter) + ")";
	}
}

package com.sushil.elasticsearch.report_generator;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.sushil.elasticsearch.config.ConfigLoader;
import com.sushil.elasticsearch.util.PdfReportUtils;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import jakarta.json.stream.JsonParser;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class DiskReportGenerator {

	// ❌ REMOVED global ENVIRONMENT_PREFIX

	private static final String METRICBEAT_INDEX = "metricbeat_index";
	private static final String SECTION_HEADER_DISK_USAGE = "Section E: DISK USAGE";
	private static final String SUBTITLE_HEADER_DISK_DETAILS = "1. Disk Usage Details for server based on mount point";

	private static final List<String> DISK_TABLE_HEADERS = Arrays.asList("IP Address", "Filesystem", "Mount Point",
			"Used %");

	/*
	 * ===== PUBLIC ENTRY ========== ✅ entity parameter added
	 */
	public static void addDiskSection(ElasticsearchClient client, Document document,
			Map<String, String> jsonFilePathMap, String entity) throws IOException, DocumentException {

		// ✅ entity-wise exclusion prefix
		String excludedEnvPrefix = getExcludedEnvPrefix(entity);

		try (InputStream dataStream = new FileInputStream(jsonFilePathMap.get("disk"))) {
			generateDiskUsageTables(client, document, dataStream, excludedEnvPrefix);
		}
	}

	/*
	 * ========== TABLE CREATION =====================
	 */
	private static void generateDiskUsageTables(ElasticsearchClient client, Document document, InputStream queryStream,
			String excludedEnvPrefix) throws IOException, DocumentException {

		PdfPTable table = new PdfPTable(DISK_TABLE_HEADERS.size());
		table.setWidthPercentage(100);
		table.setSpacingBefore(5f);
		table.setSpacingAfter(5f);
		table.setWidths(new float[] { 2f, 3f, 2.5f, 1.5f });

		// Headers
		for (String header : DISK_TABLE_HEADERS) {
			PdfPCell cell = new PdfPCell(new Phrase(header, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
			cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
			cell.setHorizontalAlignment(Element.ALIGN_CENTER);
			cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
			cell.setPadding(3);
			table.addCell(cell);
		}

		int initialRows = table.getRows().size();

		document.newPage();
		PdfReportUtils.addStyledSectionHeader(document, SECTION_HEADER_DISK_USAGE);
		PdfReportUtils.addStyledSubtitleSectionHeader(document, getSubtitleWithTimestamp());

		populateDiskUsageData(client, queryStream, table, excludedEnvPrefix);

		if (table.getRows().size() == initialRows) {
			PdfPCell noData = new PdfPCell(
					new Phrase("No Data Available", FontFactory.getFont(FontFactory.HELVETICA, 10)));
			noData.setColspan(DISK_TABLE_HEADERS.size());
			noData.setHorizontalAlignment(Element.ALIGN_CENTER);
			noData.setPadding(5);
			table.addCell(noData);
		}

		document.add(table);
	}

	/*
	 * ============= ELASTICSEARCH ============
	 */
	private static Aggregate fetchDiskAgg(ElasticsearchClient client, InputStream queryStream) throws IOException {

		JacksonJsonpMapper mapper = new JacksonJsonpMapper();
		JsonParser parser = mapper.jsonProvider().createParser(queryStream);

		SearchRequest request = SearchRequest.of(b -> b.index(METRICBEAT_INDEX).withJson(parser, mapper));

		SearchResponse<Map> response = client.search(request, Map.class);

		return response.aggregations().get("group_by_hostname");
	}

	/*
	 * ======== DATA PROCESSING (IP ROWSPAN) ===============
	 */
	private static void populateDiskUsageData(ElasticsearchClient client, InputStream dataStream, PdfPTable table,
			String excludedEnvPrefix) throws IOException {

		Aggregate ipAgg = fetchDiskAgg(client, dataStream);
		List<StringTermsBucket> ipBuckets = ipAgg.sterms().buckets().array();

		for (StringTermsBucket ipBucket : ipBuckets) {

			String ipAddress = ipBucket.key().stringValue();
			Aggregate mountAgg = ipBucket.aggregations().get("group_by_mount");
			List<StringTermsBucket> mountBuckets = mountAgg.sterms().buckets().array();

			mountBuckets.sort((a, b) -> Double.compare(getUsedPct(b), getUsedPct(a)));

			int totalRowsForIp = mountBuckets.size();
			boolean ipCellAdded = false;

			for (StringTermsBucket mountBucket : mountBuckets) {

				Aggregate currentDisk = mountBucket.aggregations().get("current_disk_space");

				for (Hit<JsonData> hit : currentDisk.topHits().hits().hits()) {

					Map<String, Object> source = hit.source().to(Map.class);

					// ✅ ENTITY-WISE ENV FILTER
					if (shouldExclude(source.get("environment"), excludedEnvPrefix))
						continue;

					String filesystem = (String) getByPath(source, "system.filesystem.device_name");
					String mountPoint = (String) getByPath(source, "system.filesystem.mount_point");

					Object usedPctObj = getByPath(source, "system.filesystem.used.pct");

					double usedPct = usedPctObj == null ? 0 : Double.parseDouble(usedPctObj.toString()) * 100;

					if (!ipCellAdded) {
						PdfPCell ipCell = PdfReportUtils.createDiskDataCell(ipAddress);
						ipCell.setRowspan(totalRowsForIp);
						ipCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
						table.addCell(ipCell);
						ipCellAdded = true;
					}

					table.addCell(PdfReportUtils.createDiskDataCell(filesystem));
					table.addCell(PdfReportUtils.createDiskDataCell(mountPoint));
					table.addCell(createHighlightedCell(String.format("%.2f%%", usedPct), usedPct));
				}
			}
		}
	}

	/*
	 * ====== ENTITY-WISE EXCLUSION ===========
	 */
	private static String getExcludedEnvPrefix(String entity) {
		return ConfigLoader.get("exclude.hostname.start_with." + entity.toUpperCase());
	}

	private static boolean shouldExclude(Object environment, String excludedEnvPrefix) {

		return excludedEnvPrefix != null && !excludedEnvPrefix.isBlank() && environment != null
				&& environment.toString().startsWith(excludedEnvPrefix);
	}

	/*
	 * ====== UTILITIES ===========
	 */
	private static PdfPCell createHighlightedCell(String text, double value) {

		PdfPCell cell = PdfReportUtils.createDiskDataCell(text);

		if (value > 80) {
			cell.setBackgroundColor(BaseColor.YELLOW);
		} else if (value > 60) {
			cell.setBackgroundColor(BaseColor.CYAN);
		}
		return cell;
	}

	private static double getUsedPct(StringTermsBucket bucket) {

		Aggregate currentDisk = bucket.aggregations().get("current_disk_space");

		List<Hit<JsonData>> hits = currentDisk.topHits().hits().hits();

		if (!hits.isEmpty()) {
			Object pct = getByPath(hits.get(0).source().to(Map.class), "system.filesystem.used.pct");

			if (pct != null) {
				return Double.parseDouble(pct.toString()) * 100;
			}
		}
		return 0;
	}

	@SuppressWarnings("unchecked")
	private static Object getByPath(Map<String, Object> source, String path) {

		String[] keys = path.split("\\.");
		Object current = source;

		for (String key : keys) {
			if (!(current instanceof Map))
				return null;
			current = ((Map<String, Object>) current).get(key);
			if (current == null)
				return null;
		}
		return current;
	}

	/* ===== Timestamp Helper ===== */
	private static String getSubtitleWithTimestamp() {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a");
		return SUBTITLE_HEADER_DISK_DETAILS + " (" + LocalDateTime.now().format(formatter) + ")";
	}
}

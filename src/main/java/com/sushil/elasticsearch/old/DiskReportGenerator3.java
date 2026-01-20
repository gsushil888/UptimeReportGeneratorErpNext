package com.sushil.elasticsearch.old;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
//import java.time.ZoneId;
//import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
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
public class DiskReportGenerator3 {

	private static final String ENVIRONMENT_PREFIX = ConfigLoader.get("exclude.hostname.start_with");
	private static final String METRICBEAT_INDEX = "metricbeat_index";
	private static final String SECTION_HEADER_DISK_USAGE = "Section E: DISK USAGE";
	private static String SUBTITLE_HEADER_DISK_DETAILS = "1.Disk Usage Details for server based on mount point";
	private static final List<String> DISK_TABLE_HEADERS = Arrays.asList("IP Address","Filesystem", "Mount Point", "Used Pct",
			"Available", "Used", "Total");
	private static List<StringTermsBucket> buckets = null;

	public static void addDiskSection(ElasticsearchClient client, Document document,
			Map<String, String> jsonFilePathMap) throws IOException, DocumentException {
		try (InputStream dataStream = new FileInputStream(jsonFilePathMap.get("disk"))) {
			generateDiskUsageTables(client, document, dataStream);
		} catch (Exception e) {
			System.err.println(e);
		}
	}

	public static void generateDiskUsageTables(ElasticsearchClient client, Document document, InputStream queryStream)
			throws IOException, DocumentException {
		PdfPTable table = new PdfPTable(DISK_TABLE_HEADERS.size());
		table.setWidthPercentage(100);
		table.setSpacingBefore(5f);
		table.setSpacingAfter(5f);

		float[] columnWidths = { 1.5f, 2, 2, 1, 1, 1, 1};
		table.setWidths(columnWidths);

		for (String header : DISK_TABLE_HEADERS) {
			PdfPCell cell = new PdfPCell(new Phrase(header, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
			cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
			cell.setHorizontalAlignment(Element.ALIGN_CENTER); // Center align header
			cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
			cell.setPadding(3);
			table.addCell(cell);
		}
		int initialRows = table.getRows().size(); // Track rows before data is added

	    document.newPage();
	    PdfReportUtils.addStyledSectionHeader(document, SECTION_HEADER_DISK_USAGE);
	    PdfReportUtils.addStyledSubtitleSectionHeader(document, SUBTITLE_HEADER_DISK_DETAILS);
	    generateDiskUsageDetailsFromElasticsearch(client, document, queryStream, table);

	    int finalRows = table.getRows().size(); // Track rows after data is added

	    // If no data was added (only headers exist), insert "No Data Available" row
	    if (finalRows == initialRows) {
	        PdfPCell noDataCell = new PdfPCell(new Phrase("No Data Available", 
	                FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.BLACK)));
	        noDataCell.setColspan(DISK_TABLE_HEADERS.size());
	        noDataCell.setHorizontalAlignment(Element.ALIGN_CENTER);
	        noDataCell.setPadding(5);
	        table.addCell(noDataCell);
	    }

	    document.add(table);
	}

	private static Aggregate fetchDiskUsageBasedOnHostName(ElasticsearchClient client, InputStream queryStream)
			throws IOException {
		JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
		JsonParser jsonParser = jsonpMapper.jsonProvider().createParser(queryStream);
		SearchRequest searchRequest = SearchRequest
				.of(b -> b.index(METRICBEAT_INDEX).withJson(jsonParser, jsonpMapper));
		SearchResponse<Map> searchResponse = client.search(searchRequest, Map.class);
		return searchResponse.aggregations().get("group_by_hostname");
	}

	private static void generateDiskUsageDetailsFromElasticsearch(ElasticsearchClient client, Document document,
			InputStream dataStream, PdfPTable table) throws IOException, DocumentException {
		Aggregate hostAgg = fetchDiskUsageBasedOnHostName(client, dataStream);
		buckets = hostAgg.sterms().buckets().array();

		for (StringTermsBucket bucket : buckets) {
			if (bucket.key().stringValue().startsWith(ENVIRONMENT_PREFIX))
				continue;

			Aggregate mountpointAgg = bucket.aggregations().get("group_by_mount");
			List<StringTermsBucket> mountBuckets = mountpointAgg.sterms().buckets().array();

			mountBuckets.sort((b1, b2) -> {
				double usedPct1 = getUsedPct(b1);
				double usedPct2 = getUsedPct(b2);
				return Double.compare(usedPct2, usedPct1);
			});

			int mountCount = mountBuckets.size();
			boolean isFirstMount = true;

			for (StringTermsBucket mountBucket : mountBuckets) {
				String mountpoint = mountBucket.key().stringValue();
				Aggregate currentDiskSpace = mountBucket.aggregations().get("current_disk_space");
				Aggregate avgDiskSpacePast = mountBucket.aggregations().get("avg_disk_space_past");
				double avgDiskSpacePct = avgDiskSpacePast.filter().aggregations().get("avg_space").avg().value();

				List<Hit<JsonData>> jsonData = currentDiskSpace.topHits().hits().hits();

				for (Hit<JsonData> actualData : jsonData) {
					Map<String, Object> systemData = (Map<String, Object>) actualData.source().to(Map.class)
							.get("system");
					Map<String, Object> filesystemData = (Map<String, Object>) systemData.get("filesystem");

					Object total = filesystemData.get("total");
					Object available = filesystemData.get("available");
					String device_name = filesystemData.get("device_name").toString();
					Map<String, Object> usedData = (Map<String, Object>) filesystemData.get("used");

					if (usedData != null) {
						Object usedPct = usedData.get("pct");
						Object usedBytes = usedData.get("bytes");

						String totalFormatted = formatBytes(total);
						String availableFormatted = formatBytes(available);
						String usedBytesFormatted = formatBytes(usedBytes);

						double usedPctValue = usedPct != null ? Double.parseDouble(usedPct.toString()) * 100 : 0.0;
						String usedPctFormatted = String.format("%.2f%%", usedPctValue);

						double avgDiskSpacePctValue = avgDiskSpacePct * 100;
						String avgDiskSpacePctFormatted = String.format("%.2f%%", avgDiskSpacePctValue);

						if (isFirstMount) {
							PdfPCell hostnameCell = PdfReportUtils.createDiskDataCell(bucket.key().stringValue());
							hostnameCell.setRowspan(mountCount);
							hostnameCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
							table.addCell(hostnameCell);
							isFirstMount = false;
						}

						table.addCell(PdfReportUtils.createDiskDataCell(device_name));
						table.addCell(PdfReportUtils.createDiskDataCell(mountpoint));
						table.addCell(createHighlightedCell(usedPctFormatted, usedPctValue));
						table.addCell(PdfReportUtils.createDiskDataCell(availableFormatted));
						table.addCell(PdfReportUtils.createDiskDataCell(usedBytesFormatted));
						table.addCell(PdfReportUtils.createDiskDataCell(totalFormatted));
//						table.addCell(createHighlightedCell(avgDiskSpacePctFormatted, avgDiskSpacePctValue));
					}
				}
			}

		}
	}

	private static PdfPCell createHighlightedCell(String text, double value) {
	    PdfPCell cell = PdfReportUtils.createDiskDataCell(text);

	    if (value > 80) {
	        cell.setBackgroundColor(BaseColor.YELLOW);
	    } else if (value > 60) {
	        cell.setBackgroundColor(BaseColor.CYAN);
	    }

	    return cell;
	}
	
	private static double getUsedPct(StringTermsBucket mountBucket) {
		Aggregate currentDiskSpace = mountBucket.aggregations().get("current_disk_space");
		List<Hit<JsonData>> jsonData = currentDiskSpace.topHits().hits().hits();

		if (!jsonData.isEmpty()) {
			Map<String, Object> systemData = (Map<String, Object>) jsonData.get(0).source().to(Map.class).get("system");
			Map<String, Object> filesystemData = (Map<String, Object>) systemData.get("filesystem");
			Map<String, Object> usedData = (Map<String, Object>) filesystemData.get("used");

			if (usedData != null && usedData.get("pct") != null) {
				return Double.parseDouble(usedData.get("pct").toString()) * 100;
			}
		}
		return 0.0;
	}

	private static String formatBytes(Object bytes) {
		if (bytes == null) {
			return "N/A";
		}
		double size = Double.parseDouble(bytes.toString());
		String[] units = { "B", "KB", "MB", "GB", "TB" };
		int unitIndex = 0;

		while (size >= 1024 && unitIndex < units.length - 1) {
			size /= 1024;
			unitIndex++;
		}

		return String.format("%.2f %s", size, units[unitIndex]);
	}
}
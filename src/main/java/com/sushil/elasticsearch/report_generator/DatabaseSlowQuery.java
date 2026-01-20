package com.sushil.elasticsearch.report_generator;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.sushil.elasticsearch.util.PdfReportUtils;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import jakarta.json.stream.JsonParser;

public class DatabaseSlowQuery {

	private static final String INDEX = "db_slow_queries_index";
	private static final String QUERY_JSON = "db_slow";

	private static final String SECTION_HEADER_DB_SLOW_USAGE = "Section F: Database Slow Query";
	private static String SUBTITLE_HEADER_DB_SLOW_ANALYTIC = "1: Query and occurance at timestamp";

	private static final List<String> DB_SLOW_ANALYTIC_HEADERS = Arrays.asList("Query Statement", "Occured",
			"Date & Time = Query Time");

	private static final NavigableMap<Double, BaseColor> QUERY_TIME_COLOR_MAP = new TreeMap<>();

	static {
		QUERY_TIME_COLOR_MAP.put(10.0, new BaseColor(70, 130, 180)); // Steel Blue
		QUERY_TIME_COLOR_MAP.put(12.0, new BaseColor(30, 144, 255)); // Dodger Blue
		QUERY_TIME_COLOR_MAP.put(15.0, new BaseColor(46, 139, 87)); // Sea Green
		QUERY_TIME_COLOR_MAP.put(18.0, new BaseColor(255, 140, 0)); // Dark Orange
		QUERY_TIME_COLOR_MAP.put(21.0, new BaseColor(128, 0, 128)); // Purple
		QUERY_TIME_COLOR_MAP.put(Double.MAX_VALUE, new BaseColor(178, 34, 34)); // Firebrick - Critical
	}

	private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

	public static void addDBSlowSection(ElasticsearchClient client, Document document,
			Map<String, String> jsonFilePathMap) throws IOException, DocumentException {
		try (InputStream dataStream = new FileInputStream(jsonFilePathMap.get(QUERY_JSON))) {

			document.newPage();
			PdfReportUtils.addStyledSectionHeader(document, SECTION_HEADER_DB_SLOW_USAGE);
			PdfReportUtils.addStyledSectionHeader(document, SUBTITLE_HEADER_DB_SLOW_ANALYTIC);

			SearchResponse<Map> searchResponse = executeElasticsearchSearch(client, dataStream);
			List<StringTermsBucket> buckets = searchResponse.aggregations().get("unique_queries").sterms().buckets()
					.array();

			PdfPTable table = createHeaderTable();

			if (buckets.isEmpty()) {
				addNoDataRow(table);
			} else {
				for (StringTermsBucket bucket : buckets) {
					addBucketRow(table, bucket);
				}
			}

			document.add(table);
		} catch (Exception e) {
			System.err.println("Error while adding DB Slow Section: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private static SearchResponse<Map> executeElasticsearchSearch(ElasticsearchClient client, InputStream jsonStream)
			throws IOException {
		JacksonJsonpMapper mapper = new JacksonJsonpMapper();
		JsonParser parser = mapper.jsonProvider().createParser(jsonStream);
		SearchRequest request = SearchRequest.of(b -> b.index(INDEX).withJson(parser, mapper));
		return client.search(request, Map.class);
	}

	private static PdfPTable createHeaderTable() throws DocumentException {
		PdfPTable table = new PdfPTable(DB_SLOW_ANALYTIC_HEADERS.size());
		table.setWidthPercentage(100);
		table.setWidths(new float[] { 3.8f, 0.9f, 2.3f });
		table.setSpacingBefore(3f);

		for (String header : DB_SLOW_ANALYTIC_HEADERS) {
			PdfPCell cell = new PdfPCell(new Phrase(header, new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD)));
			cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
			cell.setHorizontalAlignment(Element.ALIGN_CENTER);
			table.addCell(cell);
		}
		return table;
	}

	private static void addNoDataRow(PdfPTable table) {
		PdfPCell cell = new PdfPCell(
				new Phrase("No Data Available", new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, BaseColor.GRAY)));
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setColspan(DB_SLOW_ANALYTIC_HEADERS.size());
		cell.setPadding(5f);
		table.addCell(cell);
	}

	private static void addBucketRow(PdfPTable table, StringTermsBucket bucket) {
		ObjectMapper objectMapper = new ObjectMapper();

		String queryStatement = bucket.key().stringValue();
		double count = bucket.aggregations().get("query_count").valueCount().value();
		List<LongTermsBucket> timestampBuckets = bucket.aggregations().get("timestamps").lterms().buckets().array();

		table.addCell(new PdfPCell(new Phrase(queryStatement)));

		PdfPCell countCell = new PdfPCell(new Phrase(String.valueOf((int) count)));
		countCell.setHorizontalAlignment(Element.ALIGN_CENTER);
		countCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		table.addCell(countCell);

		PdfPCell timestampCell = new PdfPCell();
		timestampCell.setHorizontalAlignment(Element.ALIGN_LEFT);
		timestampCell.setVerticalAlignment(Element.ALIGN_TOP);
		timestampCell.setPadding(10f);
		timestampCell.setBorder(Rectangle.BOX);

		Font btnFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.WHITE);

		for (LongTermsBucket timeBucket : timestampBuckets) {
			try {
				Instant instant = Instant.parse(timeBucket.keyAsString());

				String dateStr = DateTimeFormatter.ofPattern("dd MMM").withZone(IST_ZONE).format(instant);
				String timeStr = DateTimeFormatter.ofPattern("hh:mm a").withZone(IST_ZONE).format(instant);

				List<Map<String, Object>> queryTimeRecords = timeBucket.aggregations().get("query_time_at_timestamp")
						.topHits().hits().hits().stream().map(hit -> (Map<String, Object>) hit.source().to(Map.class))
						.collect(Collectors.toList());

				for (Map<String, Object> queryTime : queryTimeRecords) {
					double timeValue = objectMapper.valueToTree(queryTime).at("/query_time").asDouble();
					String timeDisplay = String.format("%.2f sec", timeValue);

					Chunk chunk = new Chunk(" " + dateStr + " " + timeStr + " = " + timeDisplay + " ", btnFont);
					chunk.setBackground(getQueryTimeColor(timeValue), 3f, 1.5f, 1.5f, 1.5f);
					chunk.setTextRise(5f);

					Paragraph pillPara = new Paragraph(chunk);
					pillPara.setSpacingAfter(2f);
					timestampCell.addElement(pillPara);
				}
			} catch (Exception e) {
				System.err.println("Failed to process timestamp entry: " + e.getMessage());
			}
		}

		table.addCell(timestampCell);
	}

	private static BaseColor getQueryTimeColor(double timeInSeconds) {
		return QUERY_TIME_COLOR_MAP.ceilingEntry(timeInSeconds).getValue();
	}

}

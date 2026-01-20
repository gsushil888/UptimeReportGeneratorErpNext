//package com.sushil.elasticsearch;
//
//import java.io.FileInputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.time.Instant;
//import java.time.ZoneId;
//import java.time.format.DateTimeFormatter;
//import java.util.Arrays;
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.itextpdf.text.Document;
//import com.itextpdf.text.DocumentException;
//import com.itextpdf.text.pdf.PdfPTable;
//import com.itextpdf.text.pdf.PdfStructTreeController.returnType;
//
//import co.elastic.clients.elasticsearch.ElasticsearchClient;
//import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
//import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
//import co.elastic.clients.elasticsearch.core.SearchRequest;
//import co.elastic.clients.elasticsearch.core.SearchResponse;
//import co.elastic.clients.json.jackson.JacksonJsonpMapper;
//import jakarta.json.stream.JsonParser;
//
//public class RedisReportGenerator {
//
//	private static final String ENVIRONMENT_PREFIX = ConfigLoader.get("exclude.hostname.start_with");
//	private static final String REDIS_INDEX = "redis_monitoring_index";
//
//	private static final String SECTION_HEADER_REDIS_STATUS = "Section F: REDIS SERVICE STATUS";
//
//	public static String SUBTITLE_HEADER_CURRENT_REDIS_DETAILS = "1: Current Redis Running Details";
//	public static final List<String> CURRENT_REDIS_TABLE_HEADERS = Arrays.asList("Process", "Status",
//			"Uptime Since");
//
//	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm:ss a");
//	private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
//
//	private static List<StringTermsBucket> buckets = null;
//
//	// ---------MAIN---------
//	public static void addRedisSection(ElasticsearchClient client, Document document,
//			Map<String, String> jsonFilePathMap) throws IOException, DocumentException {
//		document.newPage();
//		PdfReportUtils.addStyledSectionHeader(document, SECTION_HEADER_REDIS_STATUS);
//		try (InputStream currentCpuStream = new FileInputStream(jsonFilePathMap.get("current_redis"))) {
//			createCurrentRedisTable(client, document, currentCpuStream);
//		}
//	}
//
//	// -------QUERY--------------
//	public static Aggregate queryHostNameAggFromElasticsearch(ElasticsearchClient client, InputStream queryStream)
//			throws IOException {
//		JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
//		JsonParser jsonParser = jsonpMapper.jsonProvider().createParser(queryStream);
//		SearchRequest searchRequest = SearchRequest.of(b -> b.index(REDIS_INDEX).withJson(jsonParser, jsonpMapper));
//		SearchResponse<Map> searchResponse = client.search(searchRequest, Map.class);
////		System.out.println("REDIS DATA:" + searchResponse);
//		return searchResponse.aggregations().get("group_by_service");
//	}
//
//	private static String formatTimestamp(JsonNode timestampNode) {
//		return Instant.parse(timestampNode.asText()).atZone(IST_ZONE).format(DATE_TIME_FORMATTER);
//	}
//
//	// ---------------CURRENT CPU-----------
////	private static void createCurrentRedisTable(ElasticsearchClient client, Document document, InputStream dataStream)
////			throws IOException, DocumentException {
////		PdfPTable currentCpuTable = initializeCurrentRedisTable(CURRENT_REDIS_TABLE_HEADERS,
////				new float[] { 2, 1, 1, 1 });
////		Aggregate processNameAgg = queryHostNameAggFromElasticsearch(client, dataStream);
////		List<StringTermsBucket> buckets = processNameAgg.sterms().buckets().array();
////
////		for (StringTermsBucket bucket : buckets) {
////			String processName = bucket.key().stringValue();
//////			System.out.println("process name: "+processName);
////
////			Aggregate currentRedisAgg = bucket.aggregations().get("current_redis");
////			
////			List<Map<String, Object>> redisRecords = extractCurrentRedisRecords(currentRedisAgg);
//////			System.out.println("REDIS RECORDS: "+redisRecords);
////			
////			for (Map<String, Object> record : redisRecords) {
//////			if (hostname.startsWith(ENVIRONMENT_PREFIX))
//////			continue;
////				String timestamp = Instant.parse(record.get("@timestamp").toString()).atZone(IST_ZONE)
////						.format(DATE_TIME_FORMATTER);
////				System.out.println("Timestamp: "+timestamp);
////				String status =record.get("status").toString();
////				String ip_address =record.get("ip_address").toString();
////				String processNames =record.get("process_name").toString();
////				String uptimeTime =record.get("uptime_time").toString();
////				String uptimeDays =record.get("uptime_days").toString();
////				
////				System.out.println(processNames+" "+ip_address+"  "+status+" "+uptimeDays+" "+ uptimeTime);
//////
//////				currentCpuTable.addCell(PdfReportUtils.createDataCell(hostname));
//////				currentCpuTable.addCell(PdfReportUtils.createDataCell(timestamp));
//////				currentCpuTable.addCell(PdfReportUtils.createDataCell(cpuSystem));
//////				currentCpuTable.addCell(PdfReportUtils.createDataCell(cpuUser));
//////				currentCpuTable.addCell(PdfReportUtils.createDataCell(cpuIO));
//////				currentCpuTable.addCell(PdfReportUtils.createDataCell(cpuSteal));
//////				currentCpuTable.addCell(PdfReportUtils.createDataCell(cpuNice));
//////				currentCpuTable.addCell(PdfReportUtils.createDataCell(cpuTotal));
//////				currentCpuTable.addCell(PdfReportUtils.createDataCell(cpuIdle));
////			}
////		}
//////		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a")
//////				.withZone(ZoneId.of("Asia/Kolkata"));
//////		SUBTITLE_HEADER_CURRENT_REDIS_DETAILS = SUBTITLE_HEADER_CURRENT_REDIS_DETAILS
//////				.concat(" (" + formatter.format(Instant.now()) + ")");
//////		PdfReportUtils.addStyledSubtitleSectionHeader(document, SUBTITLE_HEADER_CURRENT_REDIS_DETAILS);
//////		document.add(currentCpuTable);
////	}
//
//	private static void createCurrentRedisTable(ElasticsearchClient client, Document document, InputStream dataStream)
//			throws IOException, DocumentException {
//		PdfPTable currentRedisTable = initializeCurrentRedisTable(CURRENT_REDIS_TABLE_HEADERS,
//				new float[] { 3, 1, 2 });
//
//		Aggregate processNameAgg = queryHostNameAggFromElasticsearch(client, dataStream);
//		List<StringTermsBucket> buckets = processNameAgg.sterms().buckets().array();
//
//		for (StringTermsBucket bucket : buckets) {
//			String processName = bucket.key().stringValue();
//			Aggregate currentRedisAgg = bucket.aggregations().get("current_redis");
//			List<Map<String, Object>> redisRecords = extractCurrentRedisRecords(currentRedisAgg);
//
//			for (Map<String, Object> record : redisRecords) {
//				String timestamp = Instant.parse(record.get("@timestamp").toString()).atZone(IST_ZONE)
//						.format(DATE_TIME_FORMATTER);
//				String status = record.get("status").toString();
//				String ipAddress = record.getOrDefault("ip_address", "N/A").toString();
//				String processNames = record.get("process_name").toString();
//				String uptimeTime = record.get("uptime_time").toString();
//				String uptimeDays = record.get("uptime_days").toString();
//
//				currentRedisTable.addCell(PdfReportUtils.createDataCell(processNames));
//				currentRedisTable.addCell(PdfReportUtils.createDataCell(status));
//				currentRedisTable.addCell(PdfReportUtils.createDataCell(uptimeDays+ " "+uptimeTime));
//			}
//		}
//
//		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a")
//				.withZone(ZoneId.of("Asia/Kolkata"));
//		SUBTITLE_HEADER_CURRENT_REDIS_DETAILS = SUBTITLE_HEADER_CURRENT_REDIS_DETAILS
//				.concat(" (" + formatter.format(Instant.now()) + ")");
//		PdfReportUtils.addStyledSubtitleSectionHeader(document, SUBTITLE_HEADER_CURRENT_REDIS_DETAILS);
//		document.add(currentRedisTable);
//	}
//
//	private static String extractCpuPercentage(Map<String, Object> record, String path) {
//		ObjectMapper objectMapper = new ObjectMapper();
//		JsonNode node = objectMapper.valueToTree(record).at(path);
//		return node.isMissingNode() ? "-" : String.format("%.2f", node.asDouble() * 100);
//	}
//
//	public static PdfPTable initializeCurrentRedisTable(List<String> headers, float[] columnWidths)
//			throws DocumentException {
//		PdfPTable table = new PdfPTable(headers.size());
//		table.setWidthPercentage(100);
//		table.setSpacingBefore(5f);
//		table.setSpacingAfter(5f);
//		table.setWidths(columnWidths);
//
//		for (String header : headers) {
//			table.addCell(PdfReportUtils.createHeaderCell(header));
//		}
//		return table;
//	}
//
//	@SuppressWarnings("unchecked")
//	public static List<Map<String, Object>> extractCurrentRedisRecords(Aggregate aggregate) {
//		return aggregate.topHits().hits().hits().stream().map(hit -> (Map<String, Object>) hit.source().to(Map.class))
//				.collect(Collectors.toList());
//	}
//
//}



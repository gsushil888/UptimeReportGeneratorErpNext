package com.sushil.elasticsearch.dao.all;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sushil.elasticsearch.config.ConfigLoader;
import com.sushil.elasticsearch.model.cpu.CpuDetailRecord;
import com.sushil.elasticsearch.model.cpu.CpuStatsDto;
import com.sushil.elasticsearch.model.cpu.CpuThresholdRecord;
import com.sushil.elasticsearch.model.cpu.CpuUsageRecord;
import com.sushil.elasticsearch.model.memory.MemoryDetailRecord;
import com.sushil.elasticsearch.model.memory.MemoryThresholdRecord;
import com.sushil.elasticsearch.model.memory.MemoryUsageRecord;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import jakarta.json.stream.JsonParser;

@SuppressWarnings("rawtypes")
public class ReportDao {

	private static final String METRICBEAT_INDEX = "metricbeat_index";
	private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm:ss a")
			.withZone(IST_ZONE);

	private final ElasticsearchClient client;

	public ReportDao(ElasticsearchClient client) {
		this.client = client;
	}

	// 🔹 Generic Executor
	private SearchResponse<Map> execute(String index, InputStream queryStream) throws Exception {
		JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
		JsonParser jsonParser = jsonpMapper.jsonProvider().createParser(queryStream);

		SearchRequest searchRequest = SearchRequest.of(b -> b.index(index).withJson(jsonParser, jsonpMapper));
		return client.search(searchRequest, Map.class);
	}

	// =======================
	// CPU Queries
	// =======================
	public List<CpuUsageRecord> fetchCurrentCpuRecords(InputStream queryStream) throws Exception {

		Aggregate hostAgg = execute(METRICBEAT_INDEX, queryStream).aggregations().get("group_by_hostname");

		List<CpuUsageRecord> records = new ArrayList<>();
		ObjectMapper mapper = new ObjectMapper();

		for (StringTermsBucket bucket : hostAgg.sterms().buckets().array()) {

			String hostname = bucket.key().stringValue();

			Aggregate cpuAgg = bucket.aggregations().get("cpu_metrics").filter().aggregations().get("current_cpu");

			@SuppressWarnings("unchecked")
			List<Map<String, Object>> rawRecords = cpuAgg.topHits().hits().hits().stream()
					.map(hit -> (Map<String, Object>) hit.source().to(Map.class)).collect(Collectors.toList());

			for (Map<String, Object> raw : rawRecords) {

				JsonNode node = mapper.valueToTree(raw);

				String environment = node.path("environment").asText(null);
				String ipAddress = node.path("ip_address").asText(hostname);

				double system = node.at("/system/cpu/system/norm/pct").asDouble(0.0) * 100;
				double user = node.at("/system/cpu/user/norm/pct").asDouble(0.0) * 100;
				double total = node.at("/system/cpu/total/norm/pct").asDouble(0.0) * 100;

				CpuUsageRecord rec = new CpuUsageRecord();
				rec.setIp_address(ipAddress);
				rec.sethostname(hostname);
				rec.setEnvironment(environment);
				rec.setSystemPct(system);
				rec.setUserPct(user);
				rec.setTotalPct(total);

				records.add(rec);
			}
		}

//		System.out.println("CURRENT CPU RECORDS : " + records);
		return records;
	}

	public List<CpuThresholdRecord> fetchCpuThresholdExceedRecords(InputStream queryStream, double thresholdPct)
			throws Exception {

		Aggregate hostAgg = execute(METRICBEAT_INDEX, queryStream).aggregations().get("group_by_hostname");

		List<CpuThresholdRecord> exceedRecords = new ArrayList<>();
		ObjectMapper mapper = new ObjectMapper();

		for (StringTermsBucket bucket : hostAgg.sterms().buckets().array()) {

			String hostname = bucket.key().stringValue();
			Aggregate highCpuAgg = bucket.aggregations().get("high_cpu_usage");

			if (!highCpuAgg.isTopHits())
				continue;

			@SuppressWarnings("unchecked")
			List<Map<String, Object>> rawRecords = highCpuAgg.topHits().hits().hits().stream()
					.map(hit -> (Map<String, Object>) hit.source().to(Map.class))
					.sorted(Comparator.comparing(r -> Instant.parse((String) r.get("@timestamp")))).toList();

			List<List<Map<String, Object>>> windows = groupByContinuousTime(rawRecords);

			for (List<Map<String, Object>> window : windows) {

				Instant start = Instant.parse((String) window.get(0).get("@timestamp"));
				Instant end = Instant.parse((String) window.get(window.size() - 1).get("@timestamp"));

				if (Duration.between(start, end).toMinutes() < 5)
					continue;

				double avg = window.stream()
						.mapToDouble(r -> mapper.valueToTree(r).at("/system/cpu/total/norm/pct").asDouble(0.0) * 100)
						.average().orElse(0.0);

				double max = window.stream()
						.mapToDouble(r -> mapper.valueToTree(r).at("/system/cpu/total/norm/pct").asDouble(0.0) * 100)
						.max().orElse(0.0);

				if (avg > thresholdPct) {

					JsonNode node = mapper.valueToTree(window.get(0));
					String environment = node.path("environment").asText(null);
					String ipAddress = node.path("ip_address").asText(hostname);

					CpuThresholdRecord rec = new CpuThresholdRecord();
					rec.sethostname(hostname);
					rec.setIp_address(ipAddress);
					rec.setEnvironment(environment);
					rec.setStartTime(start);
					rec.setEndTime(end);
					rec.setAvgPct(avg);
					rec.setMaxPct(max);

					exceedRecords.add(rec);
				}
			}
		}

//		System.out.println("THRESHOLD CPU RECORDS : " + exceedRecords);
		return exceedRecords;
	}

	private List<List<Map<String, Object>>> groupByContinuousTime(List<Map<String, Object>> records) {
		List<List<Map<String, Object>>> windows = new ArrayList<>();
		List<Map<String, Object>> current = new ArrayList<>();
		Instant lastTs = null;

		for (Map<String, Object> record : records) {
			Instant ts = Instant.parse((String) record.get("@timestamp"));
			if (current.isEmpty() || Duration.between(lastTs, ts).toMinutes() <= 1) {
				current.add(record);
			} else {
				windows.add(new ArrayList<>(current));
				current.clear();
				current.add(record);
			}
			lastTs = ts;
		}
		if (!current.isEmpty()) {
			windows.add(current);
		}
		return windows;
	}

	public Map<String, List<CpuDetailRecord>> fetchCpuDetailRecords(InputStream queryStream) throws Exception {

		Aggregate hostAgg = execute(METRICBEAT_INDEX, queryStream).aggregations().get("group_by_hostname");

		Map<String, List<CpuDetailRecord>> detailsMap = new LinkedHashMap<>();
		ObjectMapper mapper = new ObjectMapper();

		for (StringTermsBucket bucket : hostAgg.sterms().buckets().array()) {

			String hostname = bucket.key().stringValue();
			List<CpuDetailRecord> details = new ArrayList<>();

			Aggregate detailAgg = bucket.aggregations().get("high_cpu_usage");

			if (detailAgg != null && detailAgg.isTopHits()) {

				@SuppressWarnings({ "unchecked", "unchecked" })
				List<Map<String, Object>> rawRecords = detailAgg.topHits().hits().hits().stream()
						.map(hit -> (Map<String, Object>) hit.source().to(Map.class)).collect(Collectors.toList());

				for (Map<String, Object> raw : rawRecords) {

					JsonNode node = mapper.valueToTree(raw);
					Instant ts = Instant.parse(node.get("@timestamp").asText());

					String environment = node.path("environment").asText(null);
					String ipAddress = node.path("ip_address").asText(hostname);

					double user = node.at("/system/cpu/user/norm/pct").asDouble(0.0) * 100;
					double system = node.at("/system/cpu/system/norm/pct").asDouble(0.0) * 100;
					double total = node.at("/system/cpu/total/norm/pct").asDouble(0.0) * 100;

					CpuDetailRecord rec = new CpuDetailRecord();
					rec.setHostname(hostname);
					rec.setIp_address(ipAddress);
					rec.setEnvironment(environment);
					rec.setTimestamp(ts);
					rec.setUserPct(user);
					rec.setSystemPct(system);
					rec.setTotalPct(total);

					details.add(rec);
				}
			}
			detailsMap.put(hostname, details);
		}

//		System.out.println("DETAILED CPU : " + detailsMap);
		return detailsMap;
	}

	public CpuStatsDto fetchCpuStats(String hostname, Instant gte, Instant lt) throws Exception {
		CpuStatsDto stats = new CpuStatsDto();
		Path jsonPath = Path.of(ConfigLoader.get("json.template.file.path.hardware"));
		String statsJson = Files.readString(jsonPath, StandardCharsets.UTF_8);
		String queryJson = statsJson.replace("{{gte}}", gte.toString()).replace("{{lt}}", lt.toString());
		InputStream queryStream = new ByteArrayInputStream(queryJson.getBytes(StandardCharsets.UTF_8));

		Aggregate hostAgg = execute(METRICBEAT_INDEX, queryStream).aggregations().get("group_by_hostname");
		for (StringTermsBucket bucket : hostAgg.sterms().buckets().array()) {
			if (hostname.equals(bucket.key().stringValue())) {
				Aggregate cpuStats = bucket.aggregations().get("cpu_total_usage");
				if (cpuStats != null && cpuStats.stats() != null) {
					stats.setAvgPct(cpuStats.stats().avg() * 100);
					stats.setMaxPct(cpuStats.stats().max() * 100);
				}
			}
		}
//		System.out.println("CPU STATS : " + stats);
//		System.out.println();
		return stats;
	}

	// =======================
	// Memory Queries
	// =======================

	public List<MemoryUsageRecord> fetchCurrentMemoryRecords(InputStream queryStream) throws Exception {
		Aggregate hostAgg = execute(METRICBEAT_INDEX, queryStream).aggregations().get("group_by_hostname");
		List<MemoryUsageRecord> records = new ArrayList<>();
		ObjectMapper mapper = new ObjectMapper();

		for (StringTermsBucket bucket : hostAgg.sterms().buckets().array()) {
			String hostname = bucket.key().stringValue();

			Aggregate memAgg = bucket.aggregations().get("memory_metrics");
			Aggregate currentMemAgg = memAgg.filter().aggregations().get("current_memory");

			List<Map<String, Object>> rawRecords = currentMemAgg.topHits().hits().hits().stream()
					.map(hit -> (Map<String, Object>) hit.source().to(Map.class)).collect(Collectors.toList());

			for (Map<String, Object> raw : rawRecords) {
				JsonNode node = mapper.valueToTree(raw);

				String timestamp = "-";
				if (node.has("@timestamp")) {
					timestamp = DATE_TIME_FORMATTER.format(Instant.parse(node.get("@timestamp").asText()));
				}

				String environment = node.path("environment").asText(null);
				String ipAddress = node.path("ip_address").asText(hostname);

				MemoryUsageRecord rec = new MemoryUsageRecord();
				rec.setHostname(hostname);
				rec.setIp_address(ipAddress);
				rec.setEnvironment(environment);
				rec.setTimestamp(timestamp);
				rec.setMemTotal(formatBytes(node.at("/system/memory/total").asLong(0)));
				rec.setCachedTotal(formatBytes(node.at("/system/memory/cached").asLong(0)));
				rec.setMemActualUsed(formatBytes(node.at("/system/memory/actual/used/bytes").asLong(0)));
				rec.setMemActualFree(formatBytes(node.at("/system/memory/actual/free").asLong(0)));
				rec.setMemActualUsedPct(
						String.format("%.2f", node.at("/system/memory/actual/used/pct").asDouble(0.0) * 100));
				rec.setMemPhyUsed(formatBytes(node.at("/system/memory/used/bytes").asLong(0)));
				rec.setMemPhyFree(formatBytes(node.at("/system/memory/free").asLong(0)));
				rec.setMemPhyUsedPct(String.format("%.2f", node.at("/system/memory/used/pct").asDouble(0.0) * 100));
				records.add(rec);
			}
		}
//		System.out.println();
//		System.out.println("CURRENT MEMORY: " + records);
//		System.out.println();
		return records;
	}

	public List<MemoryThresholdRecord> fetchMemoryThresholdRecords(InputStream queryStream, double thresholdPct)
			throws Exception {

		Aggregate hostAgg = execute(METRICBEAT_INDEX, queryStream).aggregations().get("group_by_hostname");

		List<MemoryThresholdRecord> exceedRecords = new ArrayList<>();
		ObjectMapper mapper = new ObjectMapper();

		for (StringTermsBucket bucket : hostAgg.sterms().buckets().array()) {

			String hostname = bucket.key().stringValue();
			Aggregate highMemAgg = bucket.aggregations().get("high_memory_usage");

			if (!highMemAgg.isTopHits())
				continue;

			List<Map<String, Object>> rawRecords = highMemAgg.topHits().hits().hits().stream()
					.map(hit -> (Map<String, Object>) hit.source().to(Map.class))
					.sorted(Comparator.comparing(r -> Instant.parse((String) r.get("@timestamp")))).toList();

			List<List<Map<String, Object>>> windows = groupByContinuousTime(rawRecords);

			for (List<Map<String, Object>> window : windows) {

				Instant start = Instant.parse((String) window.get(0).get("@timestamp"));
				Instant end = Instant.parse((String) window.get(window.size() - 1).get("@timestamp"));

				if (Duration.between(start, end).toMinutes() < 5)
					continue;

				double avg = window.stream()
						.mapToDouble(
								r -> mapper.valueToTree(r).at("/system/memory/actual/used/pct").asDouble(0.0) * 100)
						.average().orElse(0.0);

				double max = window.stream()
						.mapToDouble(
								r -> mapper.valueToTree(r).at("/system/memory/actual/used/pct").asDouble(0.0) * 100)
						.max().orElse(0.0);

				if (avg > thresholdPct) {

					JsonNode node = mapper.valueToTree(window.get(0));
					String environment = node.path("environment").asText(null);
					String ipAddress = node.path("ip_address").asText(hostname);

					MemoryThresholdRecord rec = new MemoryThresholdRecord();
					rec.setHostname(hostname);
					rec.setIpAddress(ipAddress);
					rec.setEnvironment(environment);
					rec.setStartAt(DATE_TIME_FORMATTER.format(start));
					rec.setEndAt(DATE_TIME_FORMATTER.format(end));
					rec.setAvgPct(avg);
					rec.setMaxPct(max);

					exceedRecords.add(rec);
				}
			}
		}
//		System.out.println("THRSHOLD MEMORY : " + exceedRecords);
//		System.out.println();
		return exceedRecords;
	}

	public Map<String, List<MemoryDetailRecord>> fetchMemoryDetailRecords(InputStream queryStream) throws Exception {

		Aggregate hostAgg = execute(METRICBEAT_INDEX, queryStream).aggregations().get("group_by_hostname");

		Map<String, List<MemoryDetailRecord>> detailsMap = new LinkedHashMap<>();
		ObjectMapper mapper = new ObjectMapper();

		for (StringTermsBucket bucket : hostAgg.sterms().buckets().array()) {

			String hostname = bucket.key().stringValue();
			List<MemoryDetailRecord> details = new ArrayList<>();

			Aggregate detailAgg = bucket.aggregations().get("high_memory_usage");

			if (detailAgg != null && detailAgg.isTopHits()) {

				List<Map<String, Object>> rawRecords = detailAgg.topHits().hits().hits().stream()
						.map(hit -> (Map<String, Object>) hit.source().to(Map.class)).collect(Collectors.toList());

				for (Map<String, Object> raw : rawRecords) {

					JsonNode node = mapper.valueToTree(raw);
					Instant ts = Instant.parse(node.get("@timestamp").asText());

					String environment = node.path("environment").asText(null);
					String ipAddress = node.path("ip_address").asText(hostname);

					double swapPct = node.at("/system/memory/used/pct").asDouble(0.0) * 100;
					double actualPct = node.at("/system/memory/actual/used/pct").asDouble(0.0) * 100;

					MemoryDetailRecord rec = new MemoryDetailRecord();
					rec.setHostname(hostname);
					rec.setIpAddress(ipAddress);
					rec.setEnvironment(environment);
					rec.setTimestamp(DATE_TIME_FORMATTER.format(ts));
					rec.setSwapMemoryPct(swapPct);
					rec.setActualMemoryPct(actualPct);

					details.add(rec);
				}
			}
			detailsMap.put(hostname, details);
		}
//		System.out.println("DETAILED MEMORY : " + detailsMap);
//		System.out.println();
		return detailsMap;
	}

	// =======================
	// Disk Queries
	// =======================
	public Aggregate fetchDiskHostAggregation(InputStream queryStream) throws Exception {
		return execute(METRICBEAT_INDEX, queryStream).aggregations().get("group_by_hostname");
	}

	// =======================
	// Uptime Queries
	// =======================
	public Aggregate fetchUptimeUrlAggregation(InputStream queryStream) throws Exception {
		return execute("uptime_index", queryStream).aggregations().get("group_by_url");
	}

	// ====Helper
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

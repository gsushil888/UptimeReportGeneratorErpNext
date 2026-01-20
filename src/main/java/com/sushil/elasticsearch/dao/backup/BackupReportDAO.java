package com.sushil.elasticsearch.dao.backup;


import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.sushil.elasticsearch.model.backup.BackupSummary;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import jakarta.json.stream.JsonParser;


public class BackupReportDAO {

    private static final List<String> BACKUP_INDICES = List.of(
            "dr_sync_index",
            "job_monitoring_app_index",
            "job_monitoring_db_index"
    );

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private static final List<String> BACKUP_TYPES = List.of("dr_sync", "app_backup", "db_backup");

    public List<BackupSummary> getBackupSummaries(ElasticsearchClient client, InputStream jsonStream) throws IOException {
        List<BackupSummary> summaries = new ArrayList<>();

        JacksonJsonpMapper mapper = new JacksonJsonpMapper();
        JsonParser parser = mapper.jsonProvider().createParser(jsonStream);

        SearchRequest searchRequest = SearchRequest.of(req -> req
                .index(BACKUP_INDICES)
                .withJson(parser, mapper)
        );

        SearchResponse<Map> response = client.search(searchRequest, Map.class);

        List<DateHistogramBucket> buckets = response.aggregations()
                .get("per_hour")
                .dateHistogram()
                .buckets()
                .array();

        for (DateHistogramBucket bucket : buckets) {
            String date = parseDate(bucket.keyAsString());

            for (String type : BACKUP_TYPES) {
                Optional<String> timestamp = extractTimestamp(bucket, type);
                timestamp.ifPresent(time ->
                        summaries.add(new BackupSummary(type, date, time))
                );
            }
        }

       System.out.println("Total backup summaries collected: "+summaries.size());
        return summaries;
    }

    private String parseDate(String isoString) {
        return Instant.parse(isoString).atZone(IST_ZONE).format(DATE_FORMATTER);
    }

    private Optional<String> extractTimestamp(DateHistogramBucket bucket, String backupType) {
        Aggregate filterAgg = bucket.aggregations().get(backupType);
        if (filterAgg == null || !filterAgg.isFilter()) return Optional.empty();

        Aggregate timestampAgg = filterAgg.filter().aggregations().get("backup_timestamp");
        if (timestampAgg == null || !timestampAgg.isMin()) return Optional.empty();

        String rawTimestamp = timestampAgg.min().valueAsString();
        if (rawTimestamp == null || rawTimestamp.isBlank()) return Optional.empty();

        String formattedTime = Instant.parse(rawTimestamp).atZone(IST_ZONE).format(TIME_FORMATTER);
        return Optional.of(formattedTime);
    }

}
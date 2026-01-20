package com.sushil.elasticsearch;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.sushil.elasticsearch.dao.BackupReportDAO;
import com.sushil.elasticsearch.model.BackupSummary;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

public class BackupReportGenerator {

    private static final String BACKUP_QUERY_JSON = "backups";
    private static final String SECTION_HEADER_BACKUP = "Section G: Backup Summary Per Day";
    private static final String SECTION_SUBTITLE_BACKUP_ANALYTIC = "1: Backup Times per Day";
    private static final String[] BACKUP_TYPES = {"dr_sync", "app_backup", "db_backup"};

    public static void addBackupSection(ElasticsearchClient client, Document document, Map<String, String> jsonFilePathMap) throws IOException, DocumentException {
        String queryFilePath = jsonFilePathMap.get(BACKUP_QUERY_JSON);
        if (queryFilePath == null) {
            System.err.println("No file path found for backup query JSON key: "+ BACKUP_QUERY_JSON);
            return;
        }

        try (InputStream dataStream = new FileInputStream(queryFilePath)) {
            
        	document.newPage();
            PdfReportUtils.addStyledSectionHeader(document, SECTION_HEADER_BACKUP);
            PdfReportUtils.addStyledSubtitleSectionHeader(document, SECTION_SUBTITLE_BACKUP_ANALYTIC);

            BackupReportDAO dao = new BackupReportDAO();
            List<BackupSummary> summaries = dao.getBackupSummaries(client, dataStream);
//            System.out.println("Summaries => "+ summaries);
            
            Map<String, Map<String, List<String>>> backupData = buildBackupDataMap(summaries);
            TreeSet<String> dateSet = summaries.stream()
                    .map(BackupSummary::getDate)
                    .collect(Collectors.toCollection(TreeSet::new));

            PdfPTable table = buildBackupTable(dateSet, backupData);
            document.add(table);

        } catch (Exception e) {
        	System.err.println("Error while adding Backup Section"+ e);
        }
    }

    private static Map<String, Map<String, List<String>>> buildBackupDataMap(List<BackupSummary> summaries) {
        Map<String, Map<String, List<String>>> backupData = new HashMap<>();
        for (BackupSummary summary : summaries) {
            backupData
                .computeIfAbsent(summary.getBackupType(), k -> new HashMap<>())
                .computeIfAbsent(summary.getDate(), k -> new ArrayList<>())
                .add(summary.getTimestamp());
        }
        return backupData;
    }

    private static PdfPTable buildBackupTable(TreeSet<String> dateSet, Map<String, Map<String, List<String>>> backupData) throws DocumentException {
        PdfPTable table = new PdfPTable(dateSet.size() + 1);
        table.setWidthPercentage(100);
        float[] columnWidths = new float[dateSet.size() + 1];
        Arrays.fill(columnWidths, 3f);
        table.setWidths(columnWidths);

        addTableHeaderRow(table, dateSet);
        addBackupDataRows(table, dateSet, backupData);

        return table;
    }

    private static void addTableHeaderRow(PdfPTable table, TreeSet<String> dateSet) {
        PdfPCell headerCell = createHeaderCell("Backup Type");
        table.addCell(headerCell);

        for (String date : dateSet) {
            table.addCell(createHeaderCell(date));
        }
    }

    private static void addBackupDataRows(PdfPTable table, TreeSet<String> dateSet, Map<String, Map<String, List<String>>> backupData) {
        for (String backupType : BACKUP_TYPES) {
            table.addCell(createDataCell(backupType.replace("_", " ").toUpperCase()));

            for (String date : dateSet) {
                List<String> timestamps = backupData
                        .getOrDefault(backupType, Collections.emptyMap())
                        .getOrDefault(date, Collections.emptyList());

                String content = timestamps.isEmpty() ? "N/A" : String.join(" , ", timestamps);
                PdfPCell dataCell = new PdfPCell(new Phrase(content));
                dataCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                dataCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                table.addCell(dataCell);
            }
        }
    }

    private static PdfPCell createHeaderCell(String content) {
        PdfPCell cell = new PdfPCell(new Phrase(content, new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD)));
        cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private static PdfPCell createDataCell(String content) {
        PdfPCell cell = new PdfPCell(new Phrase(content, new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD)));
        cell.setBackgroundColor(BaseColor.WHITE);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }
    
}
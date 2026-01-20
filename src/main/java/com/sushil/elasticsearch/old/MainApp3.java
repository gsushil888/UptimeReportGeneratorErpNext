//package com.sushil.elasticsearch.old;
//
//import co.elastic.clients.elasticsearch.ElasticsearchClient;
//
//import com.itextpdf.text.*;
//import com.itextpdf.text.pdf.PdfWriter;
//import com.sushil.elasticsearch.config.*;
//import com.sushil.elasticsearch.report_generator.*;
//import com.sushil.elasticsearch.util.*;
//
//import java.io.*;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.time.LocalDate;
//import java.time.format.DateTimeFormatter;
//import java.util.List;
//import java.util.*;
//
//public class MainApp3 {
//
//	private static final String ENTITIES = ConfigLoader.get("entities");
//
//	public static void main(String[] args) throws Exception {
//
//		List<String> pdfFilePaths = new ArrayList<>();
//		StringBuilder emailEntitiesList = new StringBuilder();
//		if (ENTITIES == null || ENTITIES.isBlank()) {
//			System.err.println("No entities found in configuration.");
//			return;
//		}
//		Map<String, String> templates = loadTemplatesJsonFiles();
//		if (templates.isEmpty()) {
//			System.err.println("Template json files could not be loaded.");
//			return;
//		}
//		for (String entity : ENTITIES.split(",")) {
//			entity = entity.trim().toLowerCase();
//			if (entity.isEmpty())
//				continue;
//			System.out.println("PROCESSING ENTITY: " + entity);
//			Map<String, File> tempFiles = createTempFiles(entity, templates);
//			if (tempFiles.isEmpty()) {
//				System.err.println("Skipping entity due to template processing errors: " + entity);
//				continue;
//			}
//			String pdfPath = ConfigLoader.get("pdf.file.path." + entity);
//			if (pdfPath == null || pdfPath.isBlank()) {
//				System.err.println("No PDF file path found for entity: " + entity);
//				cleanupTempFiles(tempFiles);
//				continue;
//			}
//			Map<String, String> jsonFilePathMap = new HashMap<>();
//			tempFiles.forEach((key, file) -> jsonFilePathMap.put(key, file.getAbsolutePath()));
////			System.out.println(jsonFilePathMap.containsKey("db_slow"));
//			Map<String, String> reportDateEntityData = addReportTimetToPdfPathAndReturnDatesMap(entity, pdfPath,
//					jsonFilePathMap);
//			String generatedPdfPath = reportDateEntityData.get("pdfPathWithDates");
//			if (generatedPdfPath != null) {
//				generatePdfReport(generatedPdfPath, entity, jsonFilePathMap);
//				pdfFilePaths.add(generatedPdfPath);
//				emailEntitiesList.append(entity).append(",");
//			}
//			cleanupTempFiles(tempFiles);
//			System.out.println("--------------------------");
//		}
//		if (emailEntitiesList.length() > 0) {
//			emailEntitiesList.deleteCharAt(emailEntitiesList.length() - 1);
//			String report_from = AppUptimeReportGenerator.REPORT_FROM;
//			String report_to = AppUptimeReportGenerator.REPORT_TO;
////			EmailGenerator.sendEmail(emailEntitiesList, report_from, report_to, pdfFilePaths);
//		}
////		long endTime = System.currentTimeMillis();
////		System.out.println("Completed in : " + (endTime - startTime));
//	}
//
//	// -------Load jsons from config and laod it-------
//	private static Map<String, String> loadTemplatesJsonFiles() {
//		Map<String, String> templates = new HashMap<>();
//		try {
//			templates.put("interval", loadTemplate("json.template.file.path.interval"));
//			templates.put("overall", loadTemplate("json.template.file.path.overall"));
//			templates.put("cpu", loadTemplate("json.template.file.path.cpu"));
//			templates.put("current_cpu", loadTemplate("json.template.file.path.current_cpu"));
//			templates.put("memory", loadTemplate("json.template.file.path.memory"));
//			templates.put("current_memory", loadTemplate("json.template.file.path.current_memory"));
//			templates.put("load", loadTemplate("json.template.file.path.load"));
//			templates.put("disk", loadTemplate("json.template.file.path.disk"));
////			templates.put("db_slow", loadTemplate("json.template.file.path.db_slow"));
////			templates.put("backups", loadTemplate("json.template.file.path.backups"));
////			templates.put("current_redis", loadTemplate("json.template.file.path.current_redis"));
//
//		} catch (IOException e) {
//			System.err.println("Error loading templates: " + e.getMessage());
//		}
//		return templates;
//	}
//
//	private static String loadTemplate(String configKey) throws IOException {
//		String path = ConfigLoader.get(configKey);
//		if (path == null || path.isBlank()) {
//			throw new IOException("Template path missing: " + configKey);
//		}
//		return Files.readString(Paths.get(path));
//	}
//
//	// ------Create and delete temp json files---------
//	private static Map<String, File> createTempFiles(String entity, Map<String, String> templates) {
//		Map<String, File> tempFiles = new HashMap<>();
//		try {
//			for (Map.Entry<String, String> entry : templates.entrySet()) {
//				String content = entry.getValue().replace("{{entity}}", entity.toUpperCase());
//				File tempFile = saveToTempFile(entry.getKey() + "_" + entity + ".json", content);
//				tempFiles.put(entry.getKey(), tempFile);
//			}
//		} catch (IOException e) {
//			System.err.println("Error creating temp files for entity " + entity + ": " + e.getMessage());
//		}
//		return tempFiles;
//	}
//
//	private static File saveToTempFile(String fileName, String content) throws IOException {
//		File tempFile = File.createTempFile(fileName.replace(".json", ""), ".json");
//		try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
//			writer.write(content);
//		}
//		return tempFile;
//	}
//
//	private static void cleanupTempFiles(Map<String, File> tempFiles) {
//		tempFiles.values().forEach(file -> {
//			if (file.exists()) {
//				if (file.delete()) {
////					System.out.println("Deleted temp file: " + file.getAbsolutePath());
//				} else {
//					System.err.println("Failed to delete temp file: " + file.getAbsolutePath());
//				}
//			}
//		});
//	}
//
//	// ----------Adding report times in it----------
//	public static Map<String, String> addReportTimetToPdfPathAndReturnDatesMap(String entityName, String pdfPath,
//			Map<String, String> jsonFilePathMap) {
//		Map<String, String> reportDates = new HashMap<>();
//		try (InputStream intervalJsonStream = new FileInputStream(jsonFilePathMap.get("interval"))) {
////			reportDates = addReportDatesToPdfPath();
//			reportDates = AppUptimeReportGenerator.addReportDatesToPdfPath(intervalJsonStream);
//			if (!reportDates.containsKey("from") || !reportDates.containsKey("to")) {
//				throw new IOException("Missing report date information for " + entityName);
//			}
//
//			String formattedFrom = reportDates.get("from").replace(" ", "_").replace(":", "-");
//			String formattedTo = reportDates.get("to").replace(" ", "_").replace(":", "-");
//			String pdfPathWithDates = pdfPath.replace(".pdf", "_" + formattedFrom + "_to_" + formattedTo + ".pdf");
//			System.out.println("Time interval is added to pdf path from interval json");
//
//			reportDates.put("pdfPathWithDates", pdfPathWithDates);
//		} catch (IOException e) {
//			System.err.println("Error generating report for " + entityName + ": " + e.getMessage());
//		}
//		return reportDates;
//	}
//
//	public static Map<String, String> addReportDatesToPdfPath() {
//		Map<String, String> reportDates = new HashMap<>();
//		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE_dd-MMM-yyyy");
//		LocalDate yesterday = LocalDate.now().minusDays(1);
//		LocalDate today = LocalDate.now();
//		String parsedFromPdfDate = yesterday.format(formatter).concat("_12-00_am");
//		String parsedToPdfDate = today.format(formatter).concat("_12-00_am");
//		reportDates.put("from", parsedFromPdfDate);
//		reportDates.put("to", parsedToPdfDate);
//		return reportDates;
//	}
//
//	// ------Generates Report PDF connecting elasticsearch----------
//	private static void generatePdfReport(String pdfFilePath, String reportName, Map<String, String> jsonFilePathMap)
//			throws Exception {
//		ElasticsearchClient client = null;
//		try (FileOutputStream fos = new FileOutputStream(pdfFilePath)) {
//			client = ElasticsearchClientFactory.createClient();
//			Document document = new Document();
//			PdfWriter.getInstance(document, fos);
//			document.open();
//			PdfReportUtils.addTitleToDocument(document, reportName);
//			PdfReportUtils.addReportTimeRange(AppUptimeReportGenerator.REPORT_FROM, AppUptimeReportGenerator.REPORT_TO,
//					document);
//			PdfReportUtils.addOverviewPage(document);
//			document.newPage();
//			AppUptimeReportGenerator.addUptimeSection(document, client, jsonFilePathMap);
//			CpuReportGenerator.addCpuSection(client, document, jsonFilePathMap);
//			MemoryReportGenerator.addMemorySection(client, document, jsonFilePathMap);
//			LoadNUptimeReportGenerator.addLoadSection(client, document, jsonFilePathMap);
//			DiskReportGenerator.addDiskSection(client, document, jsonFilePathMap);
////			DatabaseSlowQuery.addDBSlowSection(client, document, jsonFilePathMap);
////			BackupReportGenerator.addBackupSection(client, document, jsonFilePathMap);
//
//			document.close();
//			System.out.println("PDF generated successfully: " + pdfFilePath);
//		} catch (IOException | DocumentException e) {
//			System.err.println("Error generating PDF: " + e.getMessage());
//		} finally {
//			if (client != null) {
//				try {
//					client.close();
//				} catch (IOException e) {
//					System.err.println("Failed to close Elasticsearch client: " + e.getMessage());
//				}
//			}
//		}
//	}
//
//}
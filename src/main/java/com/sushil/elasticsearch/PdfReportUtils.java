package com.sushil.elasticsearch;

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
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PdfReportUtils {

	private static int sectionCounter = 1;

	public static void addTitleToDocument(Document document, String reportName) throws DocumentException {
		SimpleDateFormat sdf = new SimpleDateFormat("EEEE dd-MMM-yyyy hh:mm:ss a");
		String currentDateTime = sdf.format(new Date());

		Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
		Paragraph title = new Paragraph("Report (" + reportName.toUpperCase().replace("_", " ") + ")", titleFont);
		title.setAlignment(Paragraph.ALIGN_CENTER);
		title.setSpacingBefore(5f);
		title.setSpacingAfter(2f);
		document.add(title);

		Font dateFont = FontFactory.getFont(FontFactory.HELVETICA, 12, BaseColor.BLACK);
		Paragraph generatedAt = new Paragraph("Generated At: " + currentDateTime + " IST", dateFont);
		generatedAt.setAlignment(Paragraph.ALIGN_CENTER);
		generatedAt.setSpacingAfter(5f);
		document.add(generatedAt);
	}

	public static PdfPTable createTableWithUrlHeader(String url) throws DocumentException {
		PdfPTable table = new PdfPTable(2);
		table.setWidthPercentage(100);
		table.setSpacingBefore(5f);
		table.setSpacingAfter(5f);
		table.setWidths(new float[] { 3f, 2f });

		PdfPCell urlHeaderCell = new PdfPCell(
				new Paragraph("URL: " + url, FontFactory.getFont(FontFactory.HELVETICA_BOLD)));
		urlHeaderCell.setColspan(2);
		urlHeaderCell.setBackgroundColor(BaseColor.YELLOW);
		urlHeaderCell.setHorizontalAlignment(PdfPCell.ALIGN_LEFT);
		urlHeaderCell.setPadding(5f);
		table.addCell(urlHeaderCell);

		table.addCell(createHeaderCell("Timestamp"));
		table.addCell(createHeaderCell("Uptime Percentage"));

		return table;
	}

	public static void addStyledSectionHeader(Document document, String title) throws DocumentException {
		Chunk sectionChunk = new Chunk(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, BaseColor.BLACK));
		sectionChunk.setLocalDestination(title);
		Paragraph sectionHeader = new Paragraph();
		sectionHeader.setSpacingBefore(3f);
		sectionHeader.setSpacingAfter(1f);
		sectionHeader.setAlignment(Paragraph.ALIGN_LEFT);
		sectionHeader.add(sectionChunk);
		document.add(sectionHeader);
	}

	public static void addStyledSubtitleSectionHeader(Document document, String title) throws DocumentException {
		Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BaseColor.BLACK);
		Paragraph sectionHeader = new Paragraph(title, sectionFont);
		sectionHeader.setSpacingBefore(3f);
		sectionHeader.setSpacingAfter(3f);
		sectionHeader.setAlignment(Paragraph.ALIGN_LEFT);
		document.add(sectionHeader);
	}

	public static void addStyledTimeIntervalSectionHeader(Document document, String title) throws DocumentException {
		Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
		Paragraph sectionHeader = new Paragraph(title, sectionFont);
		sectionHeader.setSpacingBefore(25f);
		sectionHeader.setSpacingAfter(2f);
		sectionHeader.setAlignment(Paragraph.ALIGN_LEFT);
		document.add(sectionHeader);
	}

	public static void addNoDowntimeRow(PdfPTable table) {
		PdfPCell noDowntimeCell = new PdfPCell(new Paragraph("No Downtime"));
		noDowntimeCell.setColspan(2);
		noDowntimeCell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
		noDowntimeCell.setPadding(5f);
		table.addCell(noDowntimeCell);
	}

	public static void populateTableWithData(PdfPTable table, List<DateHistogramBucket> buckets) {
		for (DateHistogramBucket bucket : buckets) {
			double uptimeValue = bucket.aggregations().get("avg_uptime").avg().value();
			String timestamp = AppUptimeReportGenerator.formatDateInReadableFormat(bucket.keyAsString());
			String uptimePercentage = String.format("%.2f%%", uptimeValue);
			table.addCell(new Paragraph(timestamp));
			PdfPCell uptimeCell = new PdfPCell(new Paragraph(uptimePercentage));
			uptimeCell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
			uptimeCell.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE);
			if (uptimeValue < 100) {
				uptimeCell.setBackgroundColor(BaseColor.CYAN);
			}
			table.addCell(uptimeCell);
		}
	}

	static PdfPCell createHeaderCell(String text) {
		PdfPCell cell = new PdfPCell(new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD,10)));
		cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
		cell.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE);
		cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
		cell.setPadding(5f);
		return cell;
	}
	

	static PdfPCell createMemoryHeaderCell(String text) {
		PdfPCell cell = new PdfPCell(new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD,9)));
		cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
		cell.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE);
		cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
		cell.setPadding(5f);
		return cell;
	}
	
	static PdfPCell createMemorySpanHeaderCell(String text,int colSpan) {
		PdfPCell cell = new PdfPCell(new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD,10)));
		cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
		cell.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE);
		cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
		cell.setColspan(colSpan);
		cell.setPadding(5f);
		return cell;
	}


	static PdfPCell createDataCell(String text) {
		PdfPCell cell = new PdfPCell(new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA, 11)));
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setPadding(4f);
		return cell;
	}

	static PdfPCell createDiskDataCell(String text) {
		PdfPCell cell = new PdfPCell(new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA, 10)));
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setPadding(4f);
		return cell;
	}
	
	static PdfPCell createMemoryDataCell(String text) {
		PdfPCell cell = new PdfPCell(new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA, 9)));
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setPadding(4f);
		return cell;
	}

	static PdfPCell createHighlightedDataCell(String text, double value) {
		PdfPCell cell = new PdfPCell(new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA, 12)));
		cell.setPadding(5f);
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		if (value > 80) {
			cell.setBackgroundColor(BaseColor.CYAN);
		}
		return cell;
	}

	public static void addOverviewPage(Document document) throws DocumentException {
		// Create and add the main title
		Chunk overviewChunk = new Chunk("", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BaseColor.BLACK));
		overviewChunk.setUnderline(0.5f, -2f);
		overviewChunk.setLocalDestination("");
		Paragraph overviewParagraph = new Paragraph(overviewChunk);
		overviewParagraph.setAlignment(Element.ALIGN_CENTER);
		overviewParagraph.setSpacingAfter(20);
		overviewParagraph.setAlignment(overviewParagraph.ALIGN_LEFT);
		document.add(overviewParagraph);

		Map<String, String[]> sectionsWithSubsections = new LinkedHashMap<>();

		sectionsWithSubsections.put("Section A: APPLICATION URL UPTIME",
				new String[] { "Display the average URL uptime for the report's time range",
						"Show downtime percentage with 10-minute timestamp intervals" });

		sectionsWithSubsections.put("Section B: CPU USAGE",
				new String[] {"Show Current CPU usage" ,"Show timestamps when CPU usage exceeded the threshold for over 5 minutes",
						"Show detailed CPU usage with threshold timestamps" });
		
		sectionsWithSubsections.put("Section C: MEMORY USAGE",
				new String[] {"Show Current MEMORY usage" , "Show timestamps when memory usage exceeded the threshold for over 5 minutes",
						"Show detailed memory usage with threshold timestamps" });
		
		sectionsWithSubsections.put("Section D: CPU LOAD AVERAGE AND SYSTEM UPTIME",
				new String[] { "Display current cpu load average","Display the average CPU load average for the specified report time range","Display current SYSTEM UPTIME" });
		
		sectionsWithSubsections.put("Section E: DISK USAGE",
				new String[] { "Display current disk usage percentage along with average usage for each mount point" });
		
//		sectionsWithSubsections.put("Section F: Database Slow Query",
//				new String[] { "Display database slow queries with no. of occurence with timestamp" });
//
//		sectionsWithSubsections.put("Section G: Backup Summary Per Day",
//				new String[] { "Display Backup status with timestamp" });
		
		int sectionCounter = 1;
		for (Map.Entry<String, String[]> entry : sectionsWithSubsections.entrySet()) {
			String sectionTitle = entry.getKey();
			String[] subsections = entry.getValue();

			String displayText = sectionTitle.contains(":") ? toTitleCase(sectionTitle.split(":", 2)[1].trim())
					: toTitleCase(sectionTitle);
			String numberedText = sectionCounter + ". " + displayText;
			Chunk sectionLink = new Chunk(numberedText,
					FontFactory.getFont(FontFactory.HELVETICA, 12, Font.BOLD, BaseColor.BLUE));
			sectionLink.setLocalGoto(sectionTitle);
			Paragraph sectionParagraph = new Paragraph(sectionLink);
			sectionParagraph.setAlignment(Element.ALIGN_LEFT);
			sectionParagraph.setSpacingAfter(5);
			document.add(sectionParagraph);

			int subSectionCounter = 1;
			for (String sub : subsections) {
				Chunk subsectionLink = new Chunk("    " + sectionCounter + "." + subSectionCounter++ + " " + sub,
						FontFactory.getFont(FontFactory.HELVETICA, 11, BaseColor.DARK_GRAY));
				subsectionLink.setLocalGoto(sub);
				Paragraph subsectionParagraph = new Paragraph(subsectionLink);
				subsectionParagraph.setAlignment(Element.ALIGN_LEFT);
				subsectionParagraph.setSpacingAfter(3);
				document.add(subsectionParagraph);
			}

			sectionCounter++;
		}

		// Add table here
	}

	private static String maskIp(String ip) {
		String[] parts = ip.split("\\.");
		if (parts.length == 4) {
			return "X.X." + parts[2] + "." + parts[3];
		}
		return ip;
	}

	private static String toTitleCase(String input) {
		StringBuilder titleCase = new StringBuilder();
		boolean nextTitleCase = true;
		for (char c : input.toCharArray()) {
			if (Character.isWhitespace(c)) {
				titleCase.append(c);
				nextTitleCase = true;
			} else {
				titleCase.append(nextTitleCase ? Character.toUpperCase(c) : Character.toLowerCase(c));
				nextTitleCase = false;
			}
		}
		return titleCase.toString();
	}

	public static void addReportTimeRange(String REPORT_FROM, String REPORT_TO, Document document)
			throws DocumentException {
		Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BaseColor.BLACK);
		Paragraph title = new Paragraph("REPORT FROM: " + REPORT_FROM + " |  TO: " + REPORT_TO, titleFont);
		title.setAlignment(Paragraph.ALIGN_CENTER);
		title.setSpacingBefore(5f);
		title.setSpacingAfter(2f);
		document.add(title);

	}
	//=====================================
	public static PdfPCell createNoDataCell(String message, int colspan) {
	    PdfPCell cell = new PdfPCell(
	        new Phrase(message, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BaseColor.BLACK))
	    );
	    cell.setColspan(colspan);
	    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
	    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
	    cell.setPadding(6f);
	    return cell;
	}

	public static PdfPCell createMemoryDataCell(String text, boolean isNumeric) {
	    PdfPCell cell = new PdfPCell(
	        new Phrase(text != null ? text : "-", 
	            FontFactory.getFont(FontFactory.HELVETICA, 9, BaseColor.BLACK))
	    );

	    // Alignment: left for text (like IP), right/center for numbers
	    if (isNumeric) {
	        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
	    } else {
	        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
	    }

	    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
	    cell.setPadding(5f);

	    return cell;
	}
	
	


}

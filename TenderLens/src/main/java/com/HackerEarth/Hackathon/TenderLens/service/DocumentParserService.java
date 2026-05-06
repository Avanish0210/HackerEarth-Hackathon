package com.HackerEarth.Hackathon.TenderLens.service;

import com.HackerEarth.Hackathon.TenderLens.config.TenderLensProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Two-pass document parser:
 *   Pass 1 — PDFBox native text extraction (fast, works for digital PDFs)
 *   Pass 2 — Tesseract OCR (slow, for scanned/image-based PDFs)
 *
 * Falls back to OCR when native text is below MIN_TEXT_LENGTH threshold,
 * which means the PDF is likely a scanned image.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParserService {

    private static final int MIN_TEXT_LENGTH = 100; // chars per page threshold

    private final TenderLensProperties props;

    /**
     * Parse a PDF file into plain text.
     * Returns concatenated text of all pages.
     *
     * @param filePath absolute path to the PDF
     * @param forceOcr true if officer marked this as a scanned document
     */
    public ParsedDocument parse(String filePath, boolean forceOcr) {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("Document not found: " + filePath);
        }

        try {
            // Always try native extraction first
            String nativeText = extractNativeText(filePath);
            int avgCharsPerPage = estimateAvgCharsPerPage(filePath, nativeText);

            boolean useOcr = forceOcr || avgCharsPerPage < MIN_TEXT_LENGTH;

            if (useOcr) {
                log.info("PDF has low text density (avg {}/page), switching to OCR: {}", avgCharsPerPage, filePath);
                String ocrText = extractWithOcr(filePath);
                return ParsedDocument.builder()
                        .text(ocrText)
                        .ocrUsed(true)
                        .pageCount(getPageCount(filePath))
                        .filePath(filePath)
                        .build();
            } else {
                return ParsedDocument.builder()
                        .text(nativeText)
                        .ocrUsed(false)
                        .pageCount(getPageCount(filePath))
                        .filePath(filePath)
                        .build();
            }
        } catch (Exception ex) {
            log.error("Failed to parse document: {}", filePath, ex);
            throw new RuntimeException("Document parsing failed for: " + filePath, ex);
        }
    }

    // ── Native PDFBox extraction ──────────────────────────────────────────────

    private String extractNativeText(String filePath) throws IOException {
        try (PDDocument document = PDDocument.load(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private int getPageCount(String filePath) throws IOException {
        try (PDDocument document = PDDocument.load(new File(filePath))) {
            return document.getNumberOfPages();
        }
    }

    private int estimateAvgCharsPerPage(String filePath, String text) throws IOException {
        int pages = getPageCount(filePath);
        if (pages == 0) return 0;
        return text.trim().length() / pages;
    }

    // ── Tesseract OCR extraction ──────────────────────────────────────────────

    /**
     * Uses Tesseract CLI via ProcessBuilder.
     * Each page is rasterized to a temp PNG then fed to tesseract.
     */
    private String extractWithOcr(String filePath) throws IOException, InterruptedException {
        List<String> pages = new ArrayList<>();

        try (PDDocument document = PDDocument.load(new File(filePath))) {
            int numPages = document.getNumberOfPages();

            for (int i = 0; i < numPages; i++) {
                String pageText = ocrSinglePage(filePath, i);
                pages.add(pageText);
            }
        }

        return String.join("\n\n--- PAGE BREAK ---\n\n", pages);
    }

    private String ocrSinglePage(String filePath, int pageIndex) throws IOException, InterruptedException {
        // Use pdftoppm to rasterize one page, then tesseract to OCR it
        String tempBase = System.getProperty("java.io.tmpdir") + "/tl_ocr_" + System.currentTimeMillis() + "_p" + pageIndex;
        String pngPath  = tempBase + ".png";
        String txtPath  = tempBase + ".txt";

        try {
            // Rasterize page to PNG at 300 DPI
            Process raster = new ProcessBuilder(
                    "pdftoppm",
                    "-r", String.valueOf(props.getOcr().getDpi()),
                    "-f", String.valueOf(pageIndex + 1),
                    "-l", String.valueOf(pageIndex + 1),
                    "-png",
                    "-singlefile",
                    filePath,
                    tempBase
            ).redirectErrorStream(true).start();

            int rasterExit = raster.waitFor();
            if (rasterExit != 0) {
                log.warn("pdftoppm exited with {} for page {}", rasterExit, pageIndex);
                return "";
            }

            // Run Tesseract on the PNG
            Process tesseract = new ProcessBuilder(
                    "tesseract",
                    pngPath,
                    tempBase,   // output base (tesseract appends .txt)
                    "-l", props.getOcr().getLanguage(),
                    "--tessdata-dir", props.getOcr().getTessdataPath()
            ).redirectErrorStream(true).start();

            int tessExit = tesseract.waitFor();
            if (tessExit != 0) {
                log.warn("Tesseract exited with {} for page {}", tessExit, pageIndex);
                return "";
            }

            // Read output
            File txtFile = new File(txtPath);
            if (txtFile.exists()) {
                return new String(java.nio.file.Files.readAllBytes(txtFile.toPath()));
            }
            return "";

        } finally {
            // Cleanup temp files
            new File(pngPath).delete();
            new File(txtPath).delete();
        }
    }

    // ── ParsedDocument result ─────────────────────────────────────────────────

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ParsedDocument {
        private String text;
        private boolean ocrUsed;
        private int pageCount;
        private String filePath;

        public boolean isLowQuality() {
            // OCR result is considered low quality if avg chars per page is very low
            if (!ocrUsed) return false;
            return text == null || (text.trim().length() / Math.max(pageCount, 1)) < 50;
        }
    }
}
package org.example.writer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PdfConverter {

    // Common LibreOffice paths on macOS and Linux
    private static final List<String> SOFFICE_CANDIDATES = List.of(
            "soffice",
            "libreoffice",
            "/Applications/LibreOffice.app/Contents/MacOS/soffice",
            "/usr/bin/libreoffice",
            "/usr/bin/soffice",
            "/usr/local/bin/soffice"
    );

    public static void convert(Path docxPath, Path pdfPath) {
        System.out.println("Converting to PDF...");
        String soffice = findSoffice();
        if (soffice == null) {
            System.out.println("[warn] LibreOffice not found — skipping PDF generation.");
            System.out.println("       Install from https://www.libreoffice.org/download/");
            System.out.println("       Then re-run or convert manually:");
            System.out.println("       soffice --headless --convert-to pdf " + docxPath);
            return;
        }

        try {
            // LibreOffice writes the PDF next to the source file; we then move it
            ProcessBuilder pb = new ProcessBuilder(
                    soffice,
                    "--headless",
                    "--convert-to", "pdf",
                    "--outdir", docxPath.getParent().toString(),
                    docxPath.toString()
            );
            pb.redirectErrorStream(true);
            pb.inheritIO();

            int exit = pb.start().waitFor();
            if (exit != 0) {
                System.err.println("[warn] LibreOffice exited with code " + exit + " — PDF may not have been created.");
                return;
            }

            // LibreOffice names it <filename>.pdf in the same dir
            Path produced = docxPath.resolveSibling(
                    docxPath.getFileName().toString().replaceFirst("\\.docx$", ".pdf"));

            if (!produced.equals(pdfPath) && Files.exists(produced)) {
                Files.move(produced, pdfPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            if (Files.exists(pdfPath)) {
                System.out.println("PDF written: " + pdfPath);
            } else {
                System.err.println("[warn] PDF not found at expected path: " + pdfPath);
            }

        } catch (Exception e) {
            System.err.println("[warn] PDF conversion failed: " + e.getMessage());
        }
    }

    private static String findSoffice() {
        for (String candidate : SOFFICE_CANDIDATES) {
            try {
                Process p = new ProcessBuilder(candidate, "--version")
                        .redirectErrorStream(true)
                        .start();
                if (p.waitFor() == 0) return candidate;
            } catch (Exception ignored) {}
        }
        return null;
    }
}

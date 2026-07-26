package org.example;

import org.example.agent.ResumeAgent;
import org.example.config.AppConfig;
import org.example.model.Resume;
import org.example.model.TailoredResume;
import org.example.parser.ResumeParser;
import org.example.writer.PdfConverter;
import org.example.writer.StateSerializer;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws Exception {
        String jdPath = args.length > 0 ? args[0] : AppConfig.JD_PATH;

        // Read JD text
        Path jdFile = Path.of(jdPath);
        if (!Files.exists(jdFile)) {
            throw new IllegalArgumentException("Job description file not found: " + jdFile.toAbsolutePath());
        }
        String jdText = Files.readString(jdFile);

        // Derive company name from first non-blank line of JD
        String company = jdText.lines()
                .map(String::trim)
                .filter(l -> !l.isEmpty())
                .findFirst()
                .orElse("Company")
                .replaceAll("[^a-zA-Z0-9 _-]", "")
                .trim();

        // Build output paths: ~/clauderesume/{Company}/purshotam_yadav.docx|pdf
        Path outputDir  = Path.of(AppConfig.OUTPUT_BASE_DIR, company);
        Files.createDirectories(outputDir);
        Path docxPath = outputDir.resolve("purshotam_yadav.docx");
        Path pdfPath  = outputDir.resolve("purshotam_yadav.pdf");

        System.out.println("=== Resume Tailoring Agent ===");
        System.out.println("JD       : " + jdPath);
        System.out.println("Company  : " + company);
        System.out.println("Folder   : " + outputDir);

        // Parse resume from classpath
        InputStream resumeStream = Main.class.getClassLoader()
                .getResourceAsStream("resume.txt");
        if (resumeStream == null) {
            throw new IllegalStateException("resume.txt not found in classpath resources");
        }
        ResumeParser parser = new ResumeParser();
        Resume resume = parser.parse(resumeStream);
        System.out.println("Resume   : " + resume.employments().size()
                + " employers, " + resume.competencies().size() + " skill categories");

        // Run LLM pipeline
        ResumeAgent agent = new ResumeAgent();
        TailoredResume tailored = agent.tailor(resume, jdText);

        // Write .docx
        StateSerializer serializer = new StateSerializer();
        serializer.writeAndRender(resume, tailored, docxPath.toString());

        // Convert to .pdf
        PdfConverter.convert(docxPath, pdfPath);

        System.out.println("=== Done ===");
        System.out.println("DOCX: " + docxPath);
        System.out.println("PDF : " + pdfPath);
    }
}

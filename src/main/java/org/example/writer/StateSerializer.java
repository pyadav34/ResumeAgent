package org.example.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.config.AppConfig;
import org.example.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class StateSerializer {

    private final ObjectMapper mapper = new ObjectMapper();

    public void writeAndRender(Resume resume, TailoredResume tailored, String outputPath) throws Exception {
        File stateFile = Files.createTempFile("resume-state-", ".json").toFile();
        try {
            ObjectNode state = buildState(resume, tailored);
            mapper.writerWithDefaultPrettyPrinter().writeValue(stateFile, state);
            runRenderScript(stateFile.getAbsolutePath(), outputPath);
        } finally {
            stateFile.delete();
        }
    }

    private ObjectNode buildState(Resume resume, TailoredResume tailored) {
        ObjectNode root = mapper.createObjectNode();

        // contact
        ObjectNode contact = mapper.createObjectNode();
        Contact c = resume.contact();
        contact.put("name",     c.name()     != null ? c.name()     : "");
        contact.put("email",    c.email()    != null ? c.email()    : "");
        contact.put("phone",    c.phone()    != null ? c.phone()    : "");
        contact.put("address1", c.address1() != null ? c.address1() : "");
        root.set("contact", contact);

        ArrayNode linesArr    = mapper.createArrayNode();
        ArrayNode sectionsArr = mapper.createArrayNode();
        ObjectNode sectionLabels  = mapper.createObjectNode();
        ObjectNode resumeSections = mapper.createObjectNode();
        ObjectNode sectionMeta    = mapper.createObjectNode();

        // Summary section — use LLM-tailored summary
        String sumSecId = "sec-summary";
        sectionsArr.add(section(sumSecId, "summary", "Professional Summary"));
        ArrayNode sumLineIds = mapper.createArrayNode();
        String summaryText = (tailored.tailoredSummary() != null && !tailored.tailoredSummary().isBlank())
                ? tailored.tailoredSummary()
                : resume.summary();
        if (summaryText != null && !summaryText.isBlank()) {
            String lineId = "line-summary-0";
            linesArr.add(line(lineId, summaryText, null));
            sumLineIds.add(lineId);
        }
        resumeSections.set(sumSecId, sumLineIds);

        // Skills section
        String skillSecId = "sec-skills";
        sectionsArr.add(section(skillSecId, "skills", "Core Competencies"));
        ArrayNode skillLineIds = mapper.createArrayNode();
        List<CompetencyCategory> cats = tailored.orderedCategories();
        for (int i = 0; i < cats.size(); i++) {
            CompetencyCategory cat = cats.get(i);
            String lineId = "skill-" + i;
            linesArr.add(skillLine(lineId, cat.skills(), cat.category()));
            skillLineIds.add(lineId);
        }
        resumeSections.set(skillSecId, skillLineIds);

        // Employment sections
        for (TailoredEntry entry : tailored.entries()) {
            String slug   = slug(entry.companyName());
            String secId  = "sec-" + slug;
            sectionsArr.add(section(secId, "company", entry.companyName()));

            ArrayNode lineIds = mapper.createArrayNode();
            List<String> bullets = entry.selectedBullets();
            for (int i = 0; i < bullets.size(); i++) {
                String lineId = "line-" + slug + "-" + i;
                linesArr.add(line(lineId, bullets.get(i), null));
                lineIds.add(lineId);
            }
            resumeSections.set(secId, lineIds);

            ObjectNode meta = mapper.createObjectNode();
            meta.put("title",        entry.title()     != null ? entry.title()     : "");
            meta.put("startDate",    entry.startDate() != null ? entry.startDate() : "");
            meta.put("endDate",      entry.endDate()   != null ? entry.endDate()   : "");
            meta.put("technologies", "");
            meta.put("project",      "");
            sectionMeta.set(secId, meta);
        }

        root.set("lines",          linesArr);
        root.set("sections",       sectionsArr);
        root.set("sectionLabels",  sectionLabels);
        root.set("resumeSections", resumeSections);
        root.set("sectionMeta",    sectionMeta);

        return root;
    }

    private ObjectNode section(String id, String kind, String label) {
        ObjectNode n = mapper.createObjectNode();
        n.put("id",    id);
        n.put("kind",  kind);
        n.put("label", label);
        return n;
    }

    private ObjectNode line(String id, String text, String category) {
        ObjectNode n = mapper.createObjectNode();
        n.put("id",   id);
        n.put("text", text);
        if (category != null) n.put("category", category);
        else                  n.putNull("category");
        return n;
    }

    private ObjectNode skillLine(String id, String text, String category) {
        ObjectNode n = mapper.createObjectNode();
        n.put("id",       id);
        n.put("text",     text);
        n.put("category", category);
        return n;
    }

    private String slug(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    private void runRenderScript(String stateFilePath, String outputPath) throws Exception {
        String script = AppConfig.RENDER_SCRIPT;
        String style  = AppConfig.STYLE;

        // Ensure output directory exists
        File outDir = new File(outputPath).getParentFile();
        if (outDir != null) outDir.mkdirs();

        ProcessBuilder pb = new ProcessBuilder("node", script, stateFilePath, outputPath, style);
        pb.redirectErrorStream(true);
        pb.inheritIO();

        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("node render/render.js exited with code " + exitCode);
        }
        System.out.println("Written: " + outputPath);
    }
}

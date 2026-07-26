import {
  Document,
  Packer,
  Paragraph,
  TextRun,
  Table,
  TableRow,
  TableCell,
  AlignmentType,
  BorderStyle,
  WidthType,
  VerticalAlign,
  ExternalHyperlink,
  TableLayoutType,
  TabStopType,
} from "docx";
import { readFileSync } from "fs";
import { writeFile } from "fs/promises";

const HARDCODED_EDUCATION = [
  {
    boldPrefix: "Master of Science, Computer Science",
    rest: " — Georgia Institute of Technology, Atlanta, GA",
    coursework:
      "Coursework: Software Architecture, Algorithms, Big Data, Machine Learning, Reinforcement Learning",
  },
  {
    boldPrefix: "B.Tech, Electronics & Communication Engineering",
    rest: " — NIT Jaipur, India",
  },
];

const HARDCODED_CERTIFICATIONS = [
  {
    boldPrefix: "AWS Certified Solutions Architect",
    rest: " — Valid Mar 2024 – Mar 2027 (Credential ID: AWS04219627)",
  },
];

const NO_BORDER = { style: BorderStyle.NONE, size: 0, color: "FFFFFF" };
const NO_CELL_BORDERS = {
  top: NO_BORDER,
  bottom: NO_BORDER,
  left: NO_BORDER,
  right: NO_BORDER,
};
const NO_TABLE_BORDERS = {
  top: NO_BORDER,
  bottom: NO_BORDER,
  left: NO_BORDER,
  right: NO_BORDER,
  insideHorizontal: NO_BORDER,
  insideVertical: NO_BORDER,
};

function normalizeCase(s) {
  if (!s || typeof s !== "string") return s;
  return s
    .split(/(\s+)/)
    .map((part) => {
      if (/^\s+$/.test(part) || part === "") return part;
      const upperCount = (part.match(/[A-Z]/g) || []).length;
      const lowerCount = (part.match(/[a-z]/g) || []).length;
      if (upperCount === 0 && lowerCount > 0) {
        return part.charAt(0).toUpperCase() + part.slice(1);
      }
      return part;
    })
    .join("");
}

function readState(state) {
  const linesById = {};
  state.lines.forEach((l) => (linesById[l.id] = l));
  const labelOf = (sec) => state.sectionLabels[sec.id] || sec.label;
  const linesIn = (secId) =>
    (state.resumeSections[secId] || [])
      .map((id) => linesById[id])
      .filter(Boolean)
      .map((l) => l.text);
  const lineObjsIn = (secId) =>
    (state.resumeSections[secId] || [])
      .map((id) => linesById[id])
      .filter(Boolean);

  const summarySection = state.sections.find((s) => s.kind === "summary");
  const skillsSection = state.sections.find((s) => s.kind === "skills");
  const educationSec = state.sections.find((s) =>
    /education/i.test(labelOf(s))
  );
  const certSec = state.sections.find((s) => /cert/i.test(labelOf(s)));
  const employmentSecs = state.sections.filter(
    (s) => s.kind === "company" && s !== educationSec && s !== certSec
  );

  const jobs = employmentSecs
    .map((sec) => {
      const meta = state.sectionMeta[sec.id] || {};
      const bullets = linesIn(sec.id);
      const dates = [meta.startDate, meta.endDate].filter(Boolean).join(" – ");
      return {
        title: normalizeCase(meta.title || ""),
        company: normalizeCase(labelOf(sec)),
        dates,
        bullets,
        tech: (meta.technologies || "").trim(),
        project: (meta.project || "").trim(),
      };
    })
    .filter((j) => j.bullets.length);

  return {
    contact: state.contact || {},
    summary: summarySection
      ? { label: labelOf(summarySection), items: linesIn(summarySection.id) }
      : null,
    skills: skillsSection
      ? {
          label: labelOf(skillsSection),
          items: linesIn(skillsSection.id),
          lines: lineObjsIn(skillsSection.id),
        }
      : null,
    jobs,
  };
}

function groupSkillsByCategory(lines) {
  const groups = [];
  const seen = new Map();
  for (const line of lines) {
    const cat = line.category || "";
    if (!seen.has(cat)) {
      seen.set(cat, groups.length);
      groups.push({ category: cat, items: [] });
    }
    groups[seen.get(cat)].items.push(line.text);
  }
  groups.sort((a, b) => {
    if (a.category === b.category) return 0;
    if (a.category === "") return 1;
    if (b.category === "") return -1;
    return 0;
  });
  return groups;
}

function buildClassic(state, opts = {}) {
  const tight = !!opts.tight;
  const FONT = "Calibri";
  const HEADER_W = 10800, HEADER_COL = 3600;
  const JOB_W = 10800, JOB_COL = 3600;

  const SZ = 21;
  const SZ_BULLET = 21;
  const SZ_MARKER = tight ? 11 : 15;
  const SZ_SMALL = tight ? 19 : 21;
  const SZ_NAME = tight ? 32 : 36;
  const SZ_HEAD = 24;
  const HEAD_COLOR = "000000";
  const HEAD_SP = tight ? { before: 160, after: 80 } : { before: 200, after: 100 };
  const BULLET_SP = tight ? { before: 20, after: 20 } : { before: 40, after: 40 };
  const PARA_SP = tight ? { before: 60, after: 80 } : { before: 80, after: 120 };
  const HEADING_RULE = {
    bottom: { color: HEAD_COLOR, style: BorderStyle.SINGLE, size: 6, space: 1 },
  };

  const run = (text, x = {}) => new TextRun({ text, font: FONT, size: SZ, ...x });
  const runB = (text, x = {}) => run(text, { bold: true, ...x });

  const sectionHeading = (label) =>
    new Paragraph({
      children: [runB(label.toUpperCase(), { size: SZ_HEAD, color: HEAD_COLOR, characterSpacing: 20 })],
      spacing: HEAD_SP,
      border: HEADING_RULE,
    });

  const bulletPara = (text) =>
    new Paragraph({
      children: [run("• ", { size: SZ_MARKER }), run(text, { size: SZ_BULLET })],
      indent: { left: 180, hanging: 180 },
      spacing: BULLET_SP,
      alignment: AlignmentType.JUSTIFIED,
    });

  const techPara = (text) =>
    new Paragraph({
      children: [runB("Technologies:", { size: SZ_SMALL }), run(" " + text, { size: SZ_SMALL })],
      spacing: PARA_SP,
    });

  const subProjectPara = (text) =>
    new Paragraph({
      children: [run(text, { italics: true })],
      spacing: { before: 60 },
    });

  // Job entry header: title (left) | company (center) | dates (right)
  // Uses tab stops instead of a table so ATS parsers read all three fields.
  const jobHeader = (title, company, dates) =>
    new Paragraph({
      tabStops: [
        { type: TabStopType.CENTER, position: HEADER_W / 2 },
        { type: TabStopType.RIGHT,  position: HEADER_W },
      ],
      children: [
        runB(title  || ""),
        run("\t"),
        runB(company || ""),
        run("\t"),
        runB(dates  || ""),
      ],
      spacing: { before: 60, after: 60 },
      border: { bottom: { style: BorderStyle.SINGLE, size: 2, color: "C8C4CC", space: 0 } },
    });

  const { contact, summary, skills, jobs } = readState(state);

  // Header: name (center) then contact info (right-aligned lines)
  // Plain paragraphs — no table — so ATS parsers extract name and contact correctly.
  const body = [
    new Paragraph({
      alignment: AlignmentType.CENTER,
      children: [new TextRun({
        text: (contact.name || "").toUpperCase(),
        font: FONT, bold: true, smallCaps: true, size: SZ_NAME,
      })],
      spacing: { after: 20 },
    }),
  ];
  if (contact.email) {
    body.push(new Paragraph({
      alignment: AlignmentType.RIGHT,
      children: [new ExternalHyperlink({
        link: "mailto:" + contact.email,
        children: [new TextRun({ text: contact.email, font: FONT, size: SZ, style: "Hyperlink" })],
      })],
      spacing: { before: 0, after: 0 },
    }));
  }
  if (contact.phone)    body.push(new Paragraph({ alignment: AlignmentType.RIGHT, children: [run(contact.phone)],    spacing: { before: 0, after: 0 } }));
  if (contact.address1) body.push(new Paragraph({ alignment: AlignmentType.RIGHT, children: [run(contact.address1)], spacing: { before: 0, after: 0 } }));
  if (contact.address2) body.push(new Paragraph({ alignment: AlignmentType.RIGHT, children: [run(contact.address2)], spacing: { before: 0, after: 60 } }));

  const skillsBlock = (() => {
    if (opts.includeSkills === false || !skills || !skills.items.length) return null;
    const out = [sectionHeading(skills.label)];
    if (opts.skillsLayout === "categorized") {
      const groups = groupSkillsByCategory(skills.lines);
      groups.forEach((g) => {
        const children = [];
        if (g.category) children.push(runB(g.category + ":  ", { size: SZ_SMALL }));
        children.push(run(g.items.join(", "), { size: SZ_SMALL }));
        out.push(new Paragraph({ spacing: { before: 60, after: 60 }, children }));
      });
    } else {
      out.push(new Paragraph({ spacing: PARA_SP, children: [run(skills.items.join("  •  "), { size: SZ_SMALL })] }));
    }
    return out;
  })();

  if (opts.includeSummary !== false && summary && summary.items.length) {
    body.push(sectionHeading(summary.label));
    summary.items.forEach((txt) =>
      body.push(new Paragraph({ alignment: AlignmentType.JUSTIFIED, spacing: PARA_SP, children: [run(txt, { size: 21 })] }))
    );
  }

  if (skillsBlock && opts.skillsPosition !== "end") body.push(...skillsBlock);

  if (jobs.length) {
    body.push(sectionHeading("Experience"));
    jobs.forEach((j) => {
      body.push(jobHeader(j.title, j.company, j.dates));
      if (j.project) body.push(subProjectPara(j.project));
      j.bullets.forEach((b) => body.push(bulletPara(b)));
      if (j.tech) body.push(techPara(j.tech));
    });
  }

  if (skillsBlock && opts.skillsPosition === "end") body.push(...skillsBlock);

  body.push(sectionHeading("Education"));
  HARDCODED_EDUCATION.forEach((e, idx) => {
    body.push(new Paragraph({ children: [runB(e.boldPrefix), run(e.rest)], spacing: { before: idx === 0 ? 80 : 100 } }));
    if (e.coursework)
      body.push(new Paragraph({ children: [run(e.coursework)], indent: { left: 180 }, spacing: { before: 40 } }));
  });

  body.push(sectionHeading("Certification"));
  HARDCODED_CERTIFICATIONS.forEach((c) => {
    body.push(new Paragraph({ children: [runB(c.boldPrefix), run(c.rest)], spacing: { before: 80 } }));
  });

  return new Document({
    sections: [{
      properties: {
        page: { size: { width: 12240, height: 15840 }, margin: { top: 720, right: 720, bottom: 720, left: 720 } },
      },
      children: body,
    }],
  });
}

function buildModern(state, opts = {}) {
  const FONT = "Calibri";
  const ACCENT = "1F3A5F";
  const MUTED = "5A5A5A";
  const TOTAL = 10800;

  const run = (text, x = {}) => new TextRun({ text, font: FONT, size: 22, ...x });
  const runB = (text, x = {}) => run(text, { bold: true, ...x });

  const sectionHeading = (label) =>
    new Paragraph({
      children: [run(label.toUpperCase(), { bold: true, size: 24, color: ACCENT, characterSpacing: 60 })],
      spacing: { before: 280, after: 80 },
      border: { bottom: { color: ACCENT, style: BorderStyle.SINGLE, size: 16, space: 4 } },
    });

  const bulletPara = (text) =>
    new Paragraph({
      children: [run("•  ", { size: 20 }), run(text, { size: 20 })],
      indent: { left: 220, hanging: 220 },
      spacing: { before: 30, after: 30, line: 290 },
    });

  const techPara = (text) =>
    new Paragraph({
      children: [runB("Stack ", { color: ACCENT, size: 20 }), run("· " + text, { size: 20, color: MUTED })],
      spacing: { before: 100, after: 140 },
    });

  const { contact, summary, skills, jobs } = readState(state);

  const headerLeftChildren = [
    new Paragraph({
      children: [new TextRun({ text: contact.name || "", font: FONT, bold: true, size: 56, color: ACCENT, characterSpacing: 20 })],
      spacing: { after: 0 },
    }),
  ];
  if (jobs.length && jobs[0].title) {
    headerLeftChildren.push(new Paragraph({ children: [run(jobs[0].title, { size: 22, color: MUTED, italics: true })], spacing: { before: 60 } }));
  }

  const headerLeft = new TableCell({ width: { size: 7200, type: WidthType.DXA }, borders: NO_CELL_BORDERS, verticalAlign: VerticalAlign.CENTER, children: headerLeftChildren });
  const contactParas = [
    contact.email ? new Paragraph({
      alignment: AlignmentType.RIGHT,
      children: [new ExternalHyperlink({ link: "mailto:" + contact.email, children: [new TextRun({ text: contact.email, font: FONT, size: 20, color: ACCENT, style: "Hyperlink" })] })],
    }) : new Paragraph({ children: [run("")] }),
    contact.phone ? new Paragraph({ alignment: AlignmentType.RIGHT, children: [run(contact.phone, { size: 20, color: MUTED })] }) : null,
    contact.address1 ? new Paragraph({ alignment: AlignmentType.RIGHT, children: [run(contact.address1, { size: 20, color: MUTED })] }) : null,
  ].filter(Boolean);

  const headerRight = new TableCell({ width: { size: 3600, type: WidthType.DXA }, borders: NO_CELL_BORDERS, verticalAlign: VerticalAlign.CENTER, children: contactParas });

  const headerTable = new Table({
    width: { size: TOTAL, type: WidthType.DXA },
    columnWidths: [7200, 3600],
    borders: { ...NO_TABLE_BORDERS, bottom: { color: ACCENT, style: BorderStyle.SINGLE, size: 24, space: 0 } },
    rows: [new TableRow({ children: [headerLeft, headerRight] })],
  });

  const body = [headerTable];

  const skillsBlock = (() => {
    if (opts.includeSkills === false || !skills || !skills.items.length) return null;
    const out = [sectionHeading(skills.label)];
    if (opts.skillsLayout === "categorized") {
      const groups = groupSkillsByCategory(skills.lines);
      groups.forEach((g) => {
        const children = [];
        if (g.category) children.push(runB(g.category + ":  ", { color: ACCENT, size: 21 }));
        children.push(run(g.items.join(", "), { size: 21, color: "333333" }));
        out.push(new Paragraph({ spacing: { before: 60, after: 60, line: 300 }, children }));
      });
    } else {
      out.push(new Paragraph({ spacing: { before: 80, after: 120, line: 300 }, children: [run(skills.items.join("   ◆   "), { size: 21, color: "333333" })] }));
    }
    return out;
  })();

  if (opts.includeSummary !== false && summary && summary.items.length) {
    body.push(sectionHeading(summary.label));
    summary.items.forEach((txt) =>
      body.push(new Paragraph({ alignment: AlignmentType.JUSTIFIED, spacing: { before: 80, after: 120, line: 300 }, children: [run(txt, { size: 22 })] }))
    );
  }

  if (skillsBlock && opts.skillsPosition !== "end") body.push(...skillsBlock);

  if (jobs.length) {
    body.push(sectionHeading("Experience"));
    jobs.forEach((j, idx) => {
      const roleRuns = [];
      if (j.title) roleRuns.push(runB(j.title, { color: ACCENT, size: 24 }));
      if (j.title && j.company) roleRuns.push(run("  ·  ", { color: MUTED, size: 24 }));
      if (j.company) roleRuns.push(run(j.company, { size: 24 }));
      body.push(new Paragraph({ children: roleRuns, spacing: { before: 120, after: 0 } }));
      if (j.dates) body.push(new Paragraph({ children: [run(j.dates, { color: MUTED, italics: true, size: 20 })], spacing: { before: 0, after: 60 } }));
      if (j.project) body.push(new Paragraph({ children: [run(j.project, { italics: true, size: 22, color: ACCENT })], spacing: { before: 40, after: 40 } }));
      j.bullets.forEach((b) => body.push(bulletPara(b)));
      if (j.tech) body.push(techPara(j.tech));
      if (idx < jobs.length - 1) body.push(new Paragraph({ spacing: { before: 120, after: 0 }, border: { bottom: { color: "C9D2DD", style: BorderStyle.SINGLE, size: 4, space: 2 } }, children: [] }));
    });
  }

  if (skillsBlock && opts.skillsPosition === "end") body.push(...skillsBlock);

  body.push(sectionHeading("Education"));
  HARDCODED_EDUCATION.forEach((e, idx) => {
    body.push(new Paragraph({ children: [runB(e.boldPrefix, { color: ACCENT }), run(e.rest)], spacing: { before: idx === 0 ? 100 : 120 } }));
    if (e.coursework) body.push(new Paragraph({ children: [run(e.coursework, { color: MUTED, size: 20 })], indent: { left: 220 }, spacing: { before: 40 } }));
  });

  body.push(sectionHeading("Certification"));
  HARDCODED_CERTIFICATIONS.forEach((c) => {
    body.push(new Paragraph({ children: [runB(c.boldPrefix, { color: ACCENT }), run(c.rest)], spacing: { before: 100 } }));
  });

  return new Document({
    sections: [{
      properties: { page: { size: { width: 12240, height: 15840 }, margin: { top: 720, right: 720, bottom: 720, left: 720 } } },
      children: body,
    }],
  });
}

function buildEditorial(state, opts = {}) {
  const FONT = "Georgia";
  const HEAD_FONT = "Georgia";
  const ACCENT = "5A4220";
  const MUTED = "6B6256";

  const run = (text, x = {}) => new TextRun({ text, font: FONT, size: 22, ...x });
  const runB = (text, x = {}) => run(text, { bold: true, ...x });
  const runI = (text, x = {}) => run(text, { italics: true, ...x });

  const sectionHeading = (label) =>
    new Paragraph({
      alignment: AlignmentType.CENTER,
      children: [new TextRun({ text: label, font: HEAD_FONT, italics: true, size: 24, color: ACCENT, characterSpacing: 20 })],
      spacing: { before: 280, after: 0 },
      border: { bottom: { color: ACCENT, style: BorderStyle.DOUBLE, size: 6, space: 6 } },
    });

  const bulletPara = (text) =>
    new Paragraph({
      children: [run("•  ", { size: 20 }), run(text, { size: 20 })],
      indent: { left: 240, hanging: 240 },
      spacing: { before: 40, after: 40, line: 300 },
      alignment: AlignmentType.JUSTIFIED,
    });

  const techPara = (text) =>
    new Paragraph({
      alignment: AlignmentType.JUSTIFIED,
      children: [runI("Technologies — ", { color: ACCENT, size: 20 }), run(text, { size: 20, color: MUTED })],
      spacing: { before: 100, after: 140 },
    });

  const { contact, summary, skills, jobs } = readState(state);

  const headerParas = [
    new Paragraph({
      alignment: AlignmentType.CENTER,
      children: [new TextRun({ text: contact.name || "", font: HEAD_FONT, italics: true, size: 60, color: ACCENT, characterSpacing: 30 })],
      spacing: { before: 0, after: 80 },
    }),
  ];
  const contactBits = [];
  if (contact.address1 || contact.address2) contactBits.push([contact.address1, contact.address2].filter(Boolean).join(", "));
  if (contact.email) contactBits.push(contact.email);
  if (contact.phone) contactBits.push(contact.phone);
  if (contactBits.length) {
    headerParas.push(new Paragraph({
      alignment: AlignmentType.CENTER,
      children: [run(contactBits.join("   ·   "), { size: 20, color: MUTED, italics: true })],
      spacing: { after: 120 },
      border: { bottom: { color: ACCENT, style: BorderStyle.SINGLE, size: 4, space: 4 } },
    }));
  }

  const body = [...headerParas];

  const skillsBlock = (() => {
    if (opts.includeSkills === false || !skills || !skills.items.length) return null;
    const out = [sectionHeading(skills.label)];
    if (opts.skillsLayout === "categorized") {
      const groups = groupSkillsByCategory(skills.lines);
      groups.forEach((g) => {
        const children = [];
        if (g.category) children.push(runB(g.category + ":  ", { color: ACCENT }));
        children.push(run(g.items.join(", ")));
        out.push(new Paragraph({ alignment: AlignmentType.LEFT, spacing: { before: 80, after: 80, line: 300 }, indent: { left: 180 }, children }));
      });
    } else {
      out.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 120, after: 80, line: 300 }, children: [run(skills.items.join("  ·  "), { size: 21, color: "333333" })] }));
    }
    return out;
  })();

  if (opts.includeSummary !== false && summary && summary.items.length) {
    body.push(sectionHeading(summary.label));
    summary.items.forEach((txt) =>
      body.push(new Paragraph({ alignment: AlignmentType.JUSTIFIED, spacing: { before: 120, after: 80, line: 300 }, indent: { left: 180, right: 180 }, children: [run(txt, { size: 22 })] }))
    );
  }

  if (skillsBlock && opts.skillsPosition !== "end") body.push(...skillsBlock);

  if (jobs.length) {
    body.push(sectionHeading("Experience"));
    jobs.forEach((j, idx) => {
      const titleRuns = [];
      if (j.title) titleRuns.push(runB(j.title, { size: 24 }));
      if (j.title && j.company) titleRuns.push(run("  ·  ", { color: MUTED, size: 24 }));
      if (j.company) titleRuns.push(runI(j.company, { size: 24, color: ACCENT }));
      body.push(new Paragraph({ alignment: AlignmentType.LEFT, children: titleRuns, spacing: { before: 160, after: 0 } }));
      if (j.dates) body.push(new Paragraph({ children: [run(j.dates, { color: MUTED, italics: true, size: 20 })], spacing: { before: 0, after: 80 } }));
      if (j.project) body.push(new Paragraph({ children: [runI(j.project, { color: ACCENT })], spacing: { before: 40, after: 40 } }));
      j.bullets.forEach((b) => body.push(bulletPara(b)));
      if (j.tech) body.push(techPara(j.tech));
      if (idx < jobs.length - 1) body.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 100, after: 100 }, children: [run("·  ·  ·", { color: ACCENT, size: 20 })] }));
    });
  }

  if (skillsBlock && opts.skillsPosition === "end") body.push(...skillsBlock);

  body.push(sectionHeading("Education"));
  HARDCODED_EDUCATION.forEach((e, idx) => {
    body.push(new Paragraph({ children: [runB(e.boldPrefix), run(e.rest)], spacing: { before: idx === 0 ? 120 : 120 } }));
    if (e.coursework) body.push(new Paragraph({ children: [runI(e.coursework, { color: MUTED, size: 20 })], indent: { left: 240 }, spacing: { before: 40 } }));
  });

  body.push(sectionHeading("Certification"));
  HARDCODED_CERTIFICATIONS.forEach((c) => {
    body.push(new Paragraph({ children: [runB(c.boldPrefix), run(c.rest)], spacing: { before: 120 } }));
  });

  return new Document({
    sections: [{
      properties: { page: { size: { width: 12240, height: 15840 }, margin: { top: 1080, right: 1080, bottom: 1080, left: 1080 } } },
      children: body,
    }],
  });
}

// ATS-safe layout: zero tables, plain paragraphs only so parsers extract text correctly.
function buildAts(state, opts = {}) {
  const FONT = "Calibri";
  const CONTENT_WIDTH = 10800; // page 12240 - margins 720*2

  const SZ = 22;      // 11pt body
  const SZ_SM = 20;   // 10pt small / contact / dates
  const SZ_NAME = 40; // 20pt name
  const SZ_HEAD = 26; // 13pt section heading

  const run  = (text, x = {}) => new TextRun({ text, font: FONT, size: SZ, ...x });
  const runB = (text, x = {}) => run(text, { bold: true, ...x });

  const sectionHeading = (label) =>
    new Paragraph({
      children: [runB(label.toUpperCase(), { size: SZ_HEAD, characterSpacing: 20 })],
      spacing: { before: 200, after: 80 },
      border: { bottom: { color: "000000", style: BorderStyle.SINGLE, size: 6, space: 1 } },
    });

  const bulletPara = (text) =>
    new Paragraph({
      children: [run("• ", { size: SZ }), run(text, { size: SZ })],
      indent: { left: 200, hanging: 200 },
      spacing: { before: 30, after: 30 },
      alignment: AlignmentType.JUSTIFIED,
    });

  const { contact, summary, skills, jobs } = readState(state);
  const body = [];

  // Name — single large bold paragraph
  body.push(new Paragraph({
    children: [runB((contact.name || "").toUpperCase(), { size: SZ_NAME })],
    spacing: { after: 40 },
  }));

  // Contact info — single line, pipe-separated, plain text (no hyperlinks)
  const contactParts = [contact.email, contact.phone, contact.address1].filter(Boolean);
  if (contactParts.length) {
    body.push(new Paragraph({
      children: [run(contactParts.join("  |  "), { size: SZ_SM })],
      spacing: { after: 160 },
    }));
  }

  // Summary
  if (opts.includeSummary !== false && summary && summary.items.length) {
    body.push(sectionHeading("Professional Summary"));
    summary.items.forEach((txt) =>
      body.push(new Paragraph({
        children: [run(txt)],
        spacing: { before: 60, after: 60 },
        alignment: AlignmentType.JUSTIFIED,
      }))
    );
  }

  // Skills — categorized, one paragraph per category
  if (opts.includeSkills !== false && skills && skills.items.length) {
    body.push(sectionHeading("Core Competencies"));
    const groups = groupSkillsByCategory(skills.lines);
    groups.forEach((g) => {
      const children = [];
      if (g.category) children.push(runB(g.category + ": ", { size: SZ_SM }));
      children.push(run(g.items.join(", "), { size: SZ_SM }));
      body.push(new Paragraph({ spacing: { before: 40, after: 40 }, children }));
    });
  }

  // Experience — no tables; job title + dates on one line via right-aligned tab
  if (jobs.length) {
    body.push(sectionHeading("Experience"));
    jobs.forEach((j) => {
      // Title (left) | Dates (right) — single paragraph with right tab stop
      body.push(new Paragraph({
        tabStops: [{ type: TabStopType.RIGHT, position: CONTENT_WIDTH }],
        children: [
          runB(j.title || ""),
          run("\t"),
          run(j.dates || "", { size: SZ_SM, italics: true }),
        ],
        spacing: { before: 120, after: 0 },
      }));
      // Company on its own line
      if (j.company) {
        body.push(new Paragraph({
          children: [run(j.company, { italics: true })],
          spacing: { before: 0, after: 60 },
        }));
      }
      j.bullets.forEach((b) => body.push(bulletPara(b)));
    });
  }

  // Education
  body.push(sectionHeading("Education"));
  HARDCODED_EDUCATION.forEach((e, idx) => {
    body.push(new Paragraph({
      children: [runB(e.boldPrefix), run(e.rest)],
      spacing: { before: idx === 0 ? 80 : 100 },
    }));
    if (e.coursework) {
      body.push(new Paragraph({
        children: [run(e.coursework, { size: SZ_SM })],
        indent: { left: 200 },
        spacing: { before: 40 },
      }));
    }
  });

  // Certifications
  body.push(sectionHeading("Certifications"));
  HARDCODED_CERTIFICATIONS.forEach((c) => {
    body.push(new Paragraph({
      children: [runB(c.boldPrefix), run(c.rest)],
      spacing: { before: 80 },
    }));
  });

  return new Document({
    sections: [{
      properties: {
        page: { size: { width: 12240, height: 15840 }, margin: { top: 720, right: 720, bottom: 720, left: 720 } },
      },
      children: body,
    }],
  });
}

function buildDoc(state, opts) {
  switch (opts.style) {
    case "modern":    return buildModern(state, opts);
    case "editorial": return buildEditorial(state, opts);
    case "compact":   return buildClassic(state, { ...opts, tight: true });
    case "classic":   return buildClassic(state, opts);
    case "ats":       return buildAts(state, opts);
    case "classic":
    default:          return buildClassic(state, opts);
  }
}

// ---- Node.js runner ----
const stateFile  = process.argv[2];
const outputPath = process.argv[3];
const style      = process.argv[4] || "classic";

if (!stateFile || !outputPath) {
  console.error("Usage: node render.js <state.json> <output.docx> [style]");
  process.exit(1);
}

const state = JSON.parse(readFileSync(stateFile, "utf8"));
const opts = {
  style,
  includeSummary: true,
  includeSkills: true,
  skillsLayout: "categorized",
  skillsPosition: "start",
};

const doc = buildDoc(state, opts);
const buffer = await Packer.toBuffer(doc);
await writeFile(outputPath, buffer);
console.log("Written: " + outputPath);

package com.saii.quizapi.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.saii.quizapi.dto.QuizQuestionDto;
import com.saii.quizapi.dto.QuizResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

@Service
@Slf4j
public class QuizPdfService {

    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.DARK_GRAY);
    private static final Font SUBTITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA, 12, Color.GRAY);
    private static final Font HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);
    private static final Font QUESTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font BODY_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font CODE_FONT = FontFactory.getFont(FontFactory.COURIER, 10);
    private static final Font LABEL_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.DARK_GRAY);

    private static final Color PRIMARY_COLOR = new Color(41, 98, 255);
    private static final Color LIGHT_BG = new Color(245, 247, 250);

    /**
     * Génère un PDF à partir d'un quiz complet.
     *
     * @return les bytes du PDF prêt à être envoyé en réponse HTTP
     */
    public byte[] generate(final QuizResponse quiz) {
        final var out = new ByteArrayOutputStream();
        final var document = new Document(PageSize.A4, 40, 40, 50, 40);

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            addHeader(document, quiz);
            addQuestions(document, quiz);
            addFooter(document, quiz);
        } finally {
            document.close();
        }

        log.info("PDF généré pour le quiz {} : {} octets", quiz.id(), out.size());
        return out.toByteArray();
    }

    private void addHeader(final Document document, final QuizResponse quiz) {
        final var title = new Paragraph(quiz.title(), TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(8);
        document.add(title);

        final var meta = String.format("Niveau : %s  |  Durée : %d min  |  %d questions",
                quiz.targetSeniority(), quiz.durationMinutes(), quiz.questions().size());
        final var subtitle = new Paragraph(meta, SUBTITLE_FONT);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(20);
        document.add(subtitle);

        if (quiz.description() != null && !quiz.description().isBlank()) {
            final var desc = new Paragraph(quiz.description(), BODY_FONT);
            desc.setSpacingAfter(15);
            document.add(desc);
        }
    }

    private void addQuestions(final Document document, final QuizResponse quiz) {
        for (final var q : quiz.questions()) {
            addQuestionBlock(document, q);
        }
    }

    private void addQuestionBlock(final Document document, final QuizQuestionDto q) {
        final var table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10);

        table.addCell(buildQuestionHeader(q));
        table.addCell(buildQuestionBody(q));
        document.add(table);
    }

    private PdfPCell buildQuestionHeader(final QuizQuestionDto q) {
        final var versionSuffix = q.targetVersion() != null ? " " + q.targetVersion() : "";
        final var headerText = String.format("Q%d — %s%s (%s)",
                q.position(), q.technology(), versionSuffix, q.seniorityLevel());

        final var cell = new PdfPCell(new Phrase(headerText, HEADER_FONT));
        cell.setBackgroundColor(PRIMARY_COLOR);
        cell.setPadding(8);
        cell.setBorderWidth(0);
        return cell;
    }

    private PdfPCell buildQuestionBody(final QuizQuestionDto q) {
        final var cell = new PdfPCell();
        cell.setBorderWidth(0);
        cell.setBackgroundColor(LIGHT_BG);
        cell.setPadding(10);

        cell.addElement(new Paragraph(q.question(), QUESTION_FONT));

        final var answerFont = "code".equals(q.answerType()) ? CODE_FONT : BODY_FONT;

        final var answerLabel = new Paragraph("Réponse attendue :", LABEL_FONT);
        answerLabel.setSpacingBefore(8);
        cell.addElement(answerLabel);
        cell.addElement(new Paragraph(q.answer(), answerFont));

        if (q.explanation() != null && !q.explanation().isBlank()) {
            final var explLabel = new Paragraph("Explication :", LABEL_FONT);
            explLabel.setSpacingBefore(6);
            cell.addElement(explLabel);
            cell.addElement(new Paragraph(q.explanation(), BODY_FONT));
        }

        return cell;
    }

    private void addFooter(final Document document, final QuizResponse quiz) {
        final var footer = new Paragraph(
                String.format("Quiz #%d — Généré par SAII (%s)", quiz.id(), quiz.createdBy()),
                SUBTITLE_FONT
        );
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(30);
        document.add(footer);
    }
}

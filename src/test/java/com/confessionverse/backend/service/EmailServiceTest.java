package com.confessionverse.backend.service;

import jakarta.mail.Address;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    private JavaMailSender mailSender;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "fromAddress", "no-reply@example.com");
    }

    @Test
    void sendSimpleEmailShouldPopulateMessageFields() {
        emailService.sendSimpleEmail("user@example.com", "Subject line", "Body text");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();
        assertEquals("no-reply@example.com", message.getFrom());
        assertArrayEquals(new String[]{"user@example.com"}, message.getTo());
        assertEquals("Subject line", message.getSubject());
        assertEquals("Body text", message.getText());
    }

    @Test
    void sendHtmlEmailShouldPopulateMimeMessageFields() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendHtmlEmail("test@example.com", "HTML subject", "<html><body><p>Hello</p></body></html>");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        MimeMessage sent = captor.getValue();
        Address[] from = sent.getFrom();
        Address[] recipients = sent.getRecipients(MimeMessage.RecipientType.TO);

        assertEquals("HTML subject", sent.getSubject());
        assertEquals("no-reply@example.com", ((InternetAddress) from[0]).getAddress());
        assertEquals("test@example.com", ((InternetAddress) recipients[0]).getAddress());
    }

    @Test
    void sendTestEmailShouldUseExpectedSubject() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendTestEmail("test@example.com");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertEquals("Confessionverse email test", sent.getSubject());
    }
}
